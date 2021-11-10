package org.nithman.digest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    static String[] digestExtensions = {"md5", "sha1", "sha256", "sha512"};

    static Map<String, String> digestMap = new HashMap<>();
    static Map<String, Boolean> fileExtensions = new HashMap<>();
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
        return sb.append(" *").append(s.getFileName()).toString();
    }

    static void loadMapFromArrays(Map<String, String> map, String[] keys, String[] values) {
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], values[i]);
        }
    }

    static Map<String, Boolean> loadFlagMapFromArray(String[] keys) {
        Map<String, Boolean> map = new HashMap<String, Boolean>();
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], true);
        }
        return map;
    }

    static String[] loadDigests(String[] digests, String types) {
        if (types != null && types.length() > 2) {
            System.out.println("setting digests to " + types);
            return types.split(",");
        }
        return digests;
    }

    private static boolean checkFileExistence(Path base, Map<String, String> map, String[] digest) {
        for (int i = 0; i < digest.length; i++) {
            if (new File(base + "." + map.get(digest[i])).exists()) {
                return true;
            }
        }
        return false;
    }

    public static boolean digestMatch(String data, char[] buffer) {
        String data2 = String.valueOf(buffer);
        return data2.equals(data.substring(0, data2.length()));
    }

    private static void checkDigestFile(File digestFile, String data, MessageDigest messageDigest, String digestName) throws IOException {
        int expectedSize = messageDigest.digest().length * 2;
        char[] buffer = new char[expectedSize];
        int n = new InputStreamReader(new FileInputStream(digestFile), StandardCharsets.UTF_8).read(buffer);
        if (n != expectedSize) {
            System.out.println(digestFile.getName() + digestName + " bad format");
        }
        else {
            System.out.println(digestFile.getName() + " " + digestName + " " + digestMatch(data, buffer));
        }
    }

    private static InputStream generateDigestStreams(Path s, MessageDigest[] md, String[] digests) {
        InputStream is = null;
        try {
            is = Files.newInputStream(s);
            for (int i = 0; i < digests.length; i++) {
                is = new DigestInputStream(is, md[i]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return is;
    }

    private static void writeDigestFile(File digestFile, String digestData, String digestName) throws IOException {
        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(digestFile), StandardCharsets.UTF_8);
        osw.write(digestData);
        osw.write("\n");
        osw.close();
        System.out.println(digestData + " " + digestName);
    }

    public static void main( String[] args ) throws IOException {
        System.out.println("Starting " + name);
        if (args.length == 0) { System.out.println("Usage: Hasher directory [directory...]"); }
        else {
            Properties properties = getProperties();
            loadMapFromArrays(digestMap, digests, digestExtensions);
            digests = loadDigests(digests, properties.getProperty("digest.types"));
            final String mode = properties.getProperty("digest.mode");
            Map<String, Boolean> filemap = loadFlagMapFromArray(properties.getProperty("file.extensions").split(","));

            for (String arg : args) {
                System.out.println("Processing " + arg);
                Files.walk(Paths.get(arg)).filter(s -> {
                    String[] segments = s.toString().split("\\.");
                    String fileExtension = segments[segments.length - 1];
                    return filemap.containsKey(fileExtension) && filemap.get(fileExtension);
                }).forEach(s -> {
                    boolean processFile = true;
                    boolean generateFile = true;

                    if (Objects.equals(mode, "generate")) {
                        processFile = !checkFileExistence(s, digestMap, digests);
                    }
                    else if (Objects.equals(mode, "validate")) {
                        generateFile = false;
                        processFile = checkFileExistence(s, digestMap, digests);
                    }

                    if (processFile) {
                        System.out.println("generating for " + s);
                        MessageDigest[] md = null;
                        try {
                            md = generateMessageDigests(digests);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        }

                        InputStream is = generateDigestStreams(s, md, digests);
                        byte[] bytes = new byte[524288];
                        int result = 0;
                        do {
                            try {
                                result = is.read(bytes);
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        } while (result != -1);

                        for (int i = 0; i < digests.length; i++) {
                            String data = formattedDigest(md[i], s);
                            File f = new File(s + "." + digestMap.get(digests[i]));

                            if (f.exists()) {
                                try {
                                    checkDigestFile(f, data, md[i], digests[i]);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                            else if (generateFile) {
                                try {
                                    writeDigestFile(f, data, digests[i]);
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

    private static MessageDigest[] generateMessageDigests(String[] digests) throws NoSuchAlgorithmException {
        MessageDigest[] messageDigest = new MessageDigest[digests.length];
        for (int i = 0; i < digests.length; i++) {
            messageDigest[i] = MessageDigest.getInstance(digests[i]);
        }
        return messageDigest;
    }
}
