package com.leetcli.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    private ConfigManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConfigManager(tempDir);
    }

    @Test
    void get_returnsNullForMissingKey() {
        assertNull(manager.get("nonexistent"));
    }

    @Test
    void set_and_get_roundTrip() {
        manager.set("key", "value");
        assertEquals("value", manager.get("key"));
    }

    @Test
    void has_returnsFalseWhenKeyMissing() {
        assertFalse(manager.has("missing"));
    }

    @Test
    void has_returnsFalseForBlankValue() {
        manager.set("blank", "   ");
        assertFalse(manager.has("blank"));
    }

    @Test
    void has_returnsTrueForNonBlankValue() {
        manager.set("token", "abc123");
        assertTrue(manager.has("token"));
    }

    @Test
    void save_and_reload_persistsValues() throws IOException {
        manager.set("leetcode_session", "sess123");
        manager.set("csrf_token", "csrf456");
        manager.save();

        ConfigManager reloaded = new ConfigManager(tempDir);
        assertEquals("sess123", reloaded.get("leetcode_session"));
        assertEquals("csrf456", reloaded.get("csrf_token"));
    }

    @Test
    void save_createsConfigFileInDir() throws IOException {
        manager.set("k", "v");
        manager.save();

        Path configFile = tempDir.resolve("config.json");
        assertTrue(Files.exists(configFile), "config.json should exist after save");
        String content = Files.readString(configFile);
        assertTrue(content.contains("\"k\""), "config.json should contain key");
        assertTrue(content.contains("\"v\""), "config.json should contain value");
    }

    @Test
    void hasCredentials_falseWhenEmpty() {
        assertFalse(manager.has("leetcode_session"));
        assertFalse(manager.has("csrf_token"));
    }

    @Test
    void hasCredentials_trueWhenBothSet() {
        manager.set("leetcode_session", "mysession");
        manager.set("csrf_token", "mycsrf");
        assertTrue(manager.has("leetcode_session"));
        assertTrue(manager.has("csrf_token"));
    }
}
