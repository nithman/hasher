package org.nithman.digest;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * Hasher generates and validates multiple message digests for multiple files.
 */
public class Hasher {
    static final String name = "Hasher";

    static String[] digests = {"MD5", "SHA-1", "SHA-256"};
    static String[] files = {".md5", ".sha1", ".sha256"};

    static Properties getProperties(final String filename) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Properties properties = new Properties();
        try (InputStream resourceStream = loader.getResourceAsStream("application.properties")) {
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
            Properties properties = getProperties("application.properties");
            Boolean generate = Boolean.parseBoolean(properties.getProperty("hash.generate"));
            Boolean validate = Boolean.parseBoolean(properties.getProperty("hash.validate"));
            String[] extensions = properties.getProperty("file.extensions").split(",");

            for (String arg : args) {
                System.out.println(arg);
                Files.walk(Paths.get(arg)).filter(s -> {
                    for (String ex : extensions)
                        if (s.toString().endsWith(ex))
                            return true;
                    return false;
                }).forEach(s -> {
                    System.out.println("generating digests for " + s.getFileName());
                    MessageDigest[] md = new MessageDigest[digests.length];
                    InputStream is = null;
                    try {
                        for (int i = 0; i < digests.length; i++) {
                            md[i] = MessageDigest.getInstance(digests[i]);
                        }
                        is = Files.newInputStream(s);
                    }
                    catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    DigestInputStream[] dis = new DigestInputStream[3];
                    dis[0] = new DigestInputStream(is,     md[0]);
                    dis[1] = new DigestInputStream(dis[0], md[1]);
                    dis[2] = new DigestInputStream(dis[1], md[2]);
                    byte bytes[] = new byte[4092];
                    int result = 0;
                    do {
                        try {
                            result = dis[2].read(bytes);
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                    } while (result != -1);

                    for (int i = 0; i < digests.length; i++) {
                        String data = formattedDigest(md[i], s);
                        File f = new File(s.toString() + files[i]);

                        if (f.exists()) {
                            try {
                                InputStream in = new FileInputStream(f);
                                char[] cbuf = new char[md[i].digest().length * 2];
                                new InputStreamReader(new FileInputStream(f),"UTF-8").read(cbuf);
                                String data2 = String.valueOf(cbuf);
                                boolean status = data2.equals(data.substring(0, data2.length()));
                                System.out.println(s.getFileName() + " " + digests[i] + " " + status);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            try {
                                f.createNewFile();
                                OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f),"UTF-8");
                                osw.write(data);
                                osw.close();
                                System.out.println(data + " " + digests[i]);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }
        System.out.println("Ending " + name);
    }
}