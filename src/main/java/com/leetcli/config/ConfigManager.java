package com.leetcli.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Manages persistent configuration stored at ~/.leetcli/config.json
 */
public class ConfigManager {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".leetcli");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String ALGO = "AES";
    private static final byte[] KEY = "L33tCL1_S3cr3t_K".getBytes(StandardCharsets.UTF_8);

    private Map<String, String> config;

    private String encrypt(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, ALGO));
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return value;
        }
    }

    private String decrypt(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, ALGO));
            return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    public ConfigManager() {
        this.config = load();
    }

    public String get(String key) {
        return config.get(key);
    }

    public void set(String key, String value) {
        config.put(key, value);
    }

    public boolean has(String key) {
        return config.containsKey(key) && config.get(key) != null && !config.get(key).isBlank();
    }

    public void save() throws IOException {
        Files.createDirectories(CONFIG_DIR);
        
        Map<String, String> encryptedConfig = new HashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            encryptedConfig.put(entry.getKey(), encrypt(entry.getValue()));
        }
        
        Files.writeString(CONFIG_FILE, GSON.toJson(encryptedConfig));
        
        try {
            if (CONFIG_FILE.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(CONFIG_FILE, 
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                Map<String, String> loaded = GSON.fromJson(json, Map.class);
                if (loaded != null) {
                    Map<String, String> decryptedConfig = new HashMap<>();
                    for (Map.Entry<String, String> entry : loaded.entrySet()) {
                        decryptedConfig.put(entry.getKey(), decrypt(entry.getValue()));
                    }
                    return decryptedConfig;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }
}
