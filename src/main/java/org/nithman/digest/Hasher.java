package org.nithman.digest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Hasher generates and validates multiple message digests for multiple files.
 */
public class Hasher {
    static final String name = "Hasher";

    static String[] digests = {"MD5", "SHA-1", "SHA-256", "SHA-512" };
    static String[] extensions = {".md5", ".sha1", ".sha256", ".sha512"};
    static Map<String, String> filemap = new HashMap<>();
    static String propertiesFilename = "application.properties";

    static Properties getProperties() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Properties properties = new Properties();
        try (InputStream resourceStream = loader.getResourceAsStream(propertiesFilename)) {
            properties.load(resourceStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }

    static String formattedDigest(MessageDigest md, Path s) {
        StringBuilder sb = new StringBuilder();
        for(byte b : md.digest()){
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.append(" ").append(s.getFileName()).toString();
    }

    public static void main( String[] args ) throws IOException {
        System.out.println("Starting " + name);
        if (args.length == 0) { System.out.println("Usage: Hasher directory [directory...]"); }
        else {
            Properties properties = getProperties();
            for (int i = 0; i < digests.length; i++) {
                filemap.put(digests[i], extensions[i]);
            }
            String[] extensions = properties.getProperty("file.extensions").split(",");
            final String mode = properties.getProperty("digest.mode");
            final String types = properties.getProperty("digest.types");
            if (types != null && types.length() > 2) {
                System.out.println("setting digests to " + types);
                digests = types.split(",");
            }

            for (String arg : args) {
                System.out.println("Processing " + arg);
                Files.walk(Paths.get(arg)).filter(s -> { // could change to use map
                    for (String ex : extensions)
                        if (s.toString().endsWith(ex))
                            return true;
                    return false;
                }).forEach(s -> {
                    boolean processFile = true;
                    boolean generateFile = true;
                    if (Objects.equals(mode, "generate")) {
                        for (int i = 0; i < digests.length; i++) {
                            File f = new File(s + filemap.get(digests[i]));
                            if (f.exists()) {
                                processFile = false;
                                break;
                            }
                        }
                    }
                    else if (Objects.equals(mode, "validate")) {
                        generateFile = false;
                        processFile = false;
                        for (int i = 0; i < digests.length; i++) {
                            File f = new File(s + filemap.get(digests[i]));
                            if (f.exists()) {
                                processFile = true;
                                break;
                            }
                        }
                    }
                    if (processFile) {
                        System.out.println("generating for " + s);
                        MessageDigest[] md = new MessageDigest[digests.length];
                        InputStream is = null;
                        try {
                            for (int i = 0; i < digests.length; i++) {
                                md[i] = MessageDigest.getInstance(digests[i]);
                            }
                            is = Files.newInputStream(s);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }

                        DigestInputStream[] dis = new DigestInputStream[digests.length];
                        dis[0] = new DigestInputStream(is, md[0]);
                        for (int i = 1; i < digests.length; i++) {
                            dis[i] = new DigestInputStream(dis[i-1], md[i]);
                        }
                        byte[] bytes = new byte[4092];
                        int result = 0;
                        do {
                            try {
                                result = dis[digests.length - 1].read(bytes);
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        } while (result != -1);

                        for (int i = 0; i < digests.length; i++) {
                            String data = formattedDigest(md[i], s);
                            File f = new File(s + filemap.get(digests[i]));

                            if (f.exists()) {
                                try {
                                    int expectedSize = md[i].digest().length * 2;
                                    char[] buffer = new char[expectedSize];
                                    int n = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8).read(buffer);
                                    if (n != expectedSize) {
                                        System.out.println(s.getFileName() + digests[i] + " bad format");
                                    }
                                    else {
                                        String data2 = String.valueOf(buffer);
                                        boolean status = data2.equals(data.substring(0, data2.length()));
                                        System.out.println(s.getFileName() + " " + digests[i] + " " + status);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            else if (generateFile) {
                                try {
                                    OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
                                    osw.write(data);
                                    osw.close();
                                    System.out.println(data + " " + digests[i]);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                        }
                    }
                });
            }
        }
        System.out.println("Ending " + name);
    }
}
