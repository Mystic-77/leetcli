package com.leetcli.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages persistent configuration stored at ~/.leetcli/config.json
 */
public class ConfigManager {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".leetcli");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, String> config;

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
        Files.writeString(CONFIG_FILE, GSON.toJson(config));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                Map<String, String> loaded = GSON.fromJson(json, Map.class);
                return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }
}
