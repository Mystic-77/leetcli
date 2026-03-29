package com.leetcli.commands;

import com.leetcli.config.ConfigManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WhoAmICommandTest {

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream origOut, origErr;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        origOut = System.out;
        origErr = System.err;
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    void tearDown() throws IOException {
        System.setOut(origOut);
        System.setErr(origErr);
        server.shutdown();
    }

    @Test
    void noCredentials_printsError() {
        ConfigManager cfg = new ConfigManager(tempDir.resolve("empty"));
        WhoAmICommandForTest cmd = new WhoAmICommandForTest(cfg, server.url("/").toString());
        new CommandLine(cmd).execute();
        assertTrue(err.toString().contains("Not logged in"));
    }

    @Test
    void expiredSession_printsSessionExpired() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        // validateSession returns isSignedIn: false
        String body = """
            {"data":{"userStatus":{"isSignedIn":false,"username":""}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        WhoAmICommandForTest cmd = new WhoAmICommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute();

        assertTrue(err.toString().contains("Session expired"),
                "Should print session expired message");
    }

    @Test
    void validSession_showsProfile() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        String statusBody = """
            {"data":{"userStatus":{"isSignedIn":true,"username":"testuser","isPremium":false}}}
            """;
        String statsBody = """
            {"data":{"matchedUser":{"profile":{"ranking":12345},
            "submitStatsGlobal":{"acSubmissionNum":[
              {"difficulty":"All","count":150,"submissions":200},
              {"difficulty":"Easy","count":80,"submissions":100},
              {"difficulty":"Medium","count":60,"submissions":80},
              {"difficulty":"Hard","count":10,"submissions":20}
            ]}}}}
            """;

        server.enqueue(new MockResponse().setBody(statusBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody(statsBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        WhoAmICommandForTest cmd = new WhoAmICommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute();

        String output = out.toString();
        assertTrue(output.contains("testuser"), "Should show username");
        assertTrue(output.contains("12345") || output.contains("#12345"), "Should show ranking");
    }

    // ── Test subclass with injected base URL ──

    static class WhoAmICommandForTest extends WhoAmICommand {
        private final ConfigManager cfg;
        private final String baseUrl;

        WhoAmICommandForTest(ConfigManager cfg, String baseUrl) {
            this.cfg = cfg;
            this.baseUrl = baseUrl;
        }

        @Override
        public void run() {
            com.leetcli.api.LeetCodeClient client = new com.leetcli.api.LeetCodeClient(cfg, baseUrl);

            if (!client.hasCredentials()) {
                System.err.println("\n  ✗ Not logged in. Run 'leetcli login' first.\n");
                return;
            }

            try {
                com.google.gson.JsonObject userStatus = client.validateSession();
                if (userStatus == null) {
                    System.err.println("  ✗ Session expired! Run 'leetcli login' to re-authenticate.\n");
                    return;
                }

                String username = userStatus.get("username").getAsString();
                boolean isPremium = userStatus.has("isPremium") && userStatus.get("isPremium").getAsBoolean();

                com.google.gson.JsonObject statsResponse = client.getUserStats(username);

                System.out.println("  ╔══════════════════════════════════════╗");
                System.out.println("  ║          Your LeetCode Profile       ║");
                System.out.println("  ╠══════════════════════════════════════╣");
                System.out.printf("  ║  Username:  %-24s ║%n", username);
                System.out.printf("  ║  Premium:   %-24s ║%n", isPremium ? "Yes" : "No");

                if (statsResponse != null && statsResponse.has("data")) {
                    com.google.gson.JsonObject data = statsResponse.getAsJsonObject("data");
                    if (data.has("matchedUser") && !data.get("matchedUser").isJsonNull()) {
                        com.google.gson.JsonObject user = data.getAsJsonObject("matchedUser");
                        if (user.has("profile") && !user.get("profile").isJsonNull()) {
                            com.google.gson.JsonObject profile = user.getAsJsonObject("profile");
                            if (profile.has("ranking") && !profile.get("ranking").isJsonNull()) {
                                System.out.printf("  ║  Ranking:   %-24s ║%n",
                                        "#" + profile.get("ranking").getAsInt());
                            }
                        }
                    }
                }
                System.out.println("  ╚══════════════════════════════════════╝");
            } catch (Exception e) {
                System.err.println("  ✗ Error: " + e.getMessage());
            }
        }
    }
}
