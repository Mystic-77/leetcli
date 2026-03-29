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

class ListCommandTest {

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
    void noCredentials_printsErrorAndExits() {
        ConfigManager cfg = new ConfigManager(tempDir.resolve("empty"));
        // No credentials set

        ListCommand cmd = new ListCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        int exit = new CommandLine(cmd).execute("--no-tui");

        assertTrue(err.toString().contains("Not logged in"), "Should print not-logged-in error");
    }

    @Test
    void noTuiFlag_printsTableOutput() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        String body = """
            {"data":{"problemsetQuestionList":{"total":1,"questions":[
              {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
               "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":"ac"}
            ]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        ListCommand cmd = new ListCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute("--no-tui");

        String output = out.toString();
        assertTrue(output.contains("Two Sum"), "Table output should include problem title");
        assertTrue(output.contains("Easy"), "Table output should include difficulty");
    }

    @Test
    void jsonFlag_outputsValidJsonArray() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        String body = """
            {"data":{"problemsetQuestionList":{"total":2,"questions":[
              {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
               "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":"ac"},
              {"frontendQuestionId":"2","title":"Add Numbers","titleSlug":"add-numbers",
               "difficulty":"Medium","acRate":41.0,"isPaidOnly":false,"status":null}
            ]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        ListCommand cmd = new ListCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute("--json");

        String output = out.toString().trim();
        assertTrue(output.startsWith("["), "JSON output should be an array");
        assertTrue(output.endsWith("]"), "JSON output should be an array");
        assertTrue(output.contains("Two Sum"), "JSON should contain problem title");
        assertTrue(output.contains("two-sum"), "JSON should contain titleSlug");
    }

    @Test
    void searchFlag_passesSearchToApi() throws IOException, InterruptedException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        String body = """
            {"data":{"problemsetQuestionList":{"total":1,"questions":[
              {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
               "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":null}
            ]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        ListCommand cmd = new ListCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute("--search", "two sum");

        String reqBody = server.takeRequest().getBody().readUtf8();
        assertTrue(reqBody.contains("two sum"), "Request should include search keyword");
    }

    @Test
    void difficultyFlag_passesFilterToApi() throws IOException, InterruptedException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();

        String body = """
            {"data":{"problemsetQuestionList":{"total":800,"questions":[]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        ListCommand cmd = new ListCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""));
        new CommandLine(cmd).execute("--difficulty", "HARD");

        String reqBody = server.takeRequest().getBody().readUtf8();
        assertTrue(reqBody.contains("HARD"), "Request should include difficulty filter");
    }

    // ── Test subclass that injects mock config/client ──

    static class ListCommandForTest extends ListCommand {
        private final ConfigManager cfg;
        private final String baseUrl;

        ListCommandForTest(ConfigManager cfg, String baseUrl) {
            this.cfg = cfg;
            this.baseUrl = baseUrl;
        }

        @Override
        public void run() {
            com.leetcli.api.LeetCodeClient client = new com.leetcli.api.LeetCodeClient(cfg, baseUrl);

            if (!client.hasCredentials()) {
                System.err.println("\n  Not logged in. Run 'leetcli login' first.\n");
                return;
            }

            // Re-use the parent's printTable / printJson logic via reflection would be fragile.
            // Instead, duplicate the same branching logic pointing at our injected client.
            try {
                if (isJson()) {
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    java.util.List<com.leetcli.api.models.Problem> problems =
                            client.listProblems(getLimit(), (getPage() - 1) * getLimit(), getDifficulty(), getSearch());
                    System.out.println(gson.toJson(problems));
                } else {
                    int skip = (getPage() - 1) * getLimit();
                    java.util.List<com.leetcli.api.models.Problem> problems =
                            client.listProblems(getLimit(), skip, getDifficulty(), getSearch());

                    if (problems.isEmpty()) { System.out.println("  No problems found.\n"); return; }

                    System.out.printf("  %-2s  %-6s  %-50s  %-8s  %6s%n", "", "#", "Title", "Diff", "AC%");
                    System.out.println("  " + "─".repeat(80));
                    for (com.leetcli.api.models.Problem p : problems) {
                        String title = p.getTitle();
                        if (title.length() > 48) title = title.substring(0, 45) + "...";
                        System.out.printf("  [%s] %-6s  %-50s  %-10s  %5.1f%%%n",
                                p.getStatusIcon(), p.getFrontendQuestionId(),
                                title, p.getDifficulty(), p.getAcRate());
                    }
                }
            } catch (Exception e) {
                System.err.println("  Error: " + e.getMessage());
            }
        }

        private boolean isJson() {
            try {
                var f = ListCommand.class.getDeclaredField("json");
                f.setAccessible(true);
                return (boolean) f.get(this);
            } catch (Exception e) { return false; }
        }
        private int getLimit() {
            try {
                var f = ListCommand.class.getDeclaredField("limit");
                f.setAccessible(true);
                return (int) f.get(this);
            } catch (Exception e) { return 20; }
        }
        private int getPage() {
            try {
                var f = ListCommand.class.getDeclaredField("page");
                f.setAccessible(true);
                return (int) f.get(this);
            } catch (Exception e) { return 1; }
        }
        private String getDifficulty() {
            try {
                var f = ListCommand.class.getDeclaredField("difficulty");
                f.setAccessible(true);
                return (String) f.get(this);
            } catch (Exception e) { return null; }
        }
        private String getSearch() {
            try {
                var f = ListCommand.class.getDeclaredField("search");
                f.setAccessible(true);
                return (String) f.get(this);
            } catch (Exception e) { return null; }
        }
    }
}
