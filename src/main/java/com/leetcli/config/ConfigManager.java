package com.leetcli.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent configuration stored at ~/.leetcli/config.json
 */
public class ConfigManager {

    private static final Path DEFAULT_CONFIG_DIR =
            Path.of(System.getProperty("user.home"), ".leetcli");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir;
    private final Path configFile;
    private final Map<String, String> config = new ConcurrentHashMap<>();

    public ConfigManager() {
        this(DEFAULT_CONFIG_DIR);
    }

    /** Test constructor — inject a custom config directory. */
    public ConfigManager(Path configDir) {
        this.configDir = configDir;
        this.configFile = configDir.resolve("config.json");
        this.config.putAll(load());
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

    public synchronized void save() throws IOException {
        Files.createDirectories(configDir);
        Files.writeString(configFile, GSON.toJson(new HashMap<>(config)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load() {
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                Map<String, String> loaded = GSON.fromJson(json, Map.class);
                return loaded != null ? loaded : new HashMap<>();
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public Path getConfigDir() {
        return configDir;
    }

    public static Path getDefaultConfigDir() {
        return DEFAULT_CONFIG_DIR;
    }
}
