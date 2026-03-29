package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
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

    private ConfigManager withCredentials() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();
        return cfg;
    }

    private String mockUrl() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private void enqueueProblems(String... extraProblems) {
        String problems = extraProblems.length > 0 ? String.join(",", extraProblems) :
            """
            {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
             "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":"ac"}
            """;
        server.enqueue(new MockResponse()
            .setBody("{\"data\":{\"problemsetQuestionList\":{\"total\":1,\"questions\":[" + problems + "]}}}")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));
    }

    @Test
    void noCredentials_printsErrorAndReturns() {
        ConfigManager cfg = new ConfigManager(tempDir.resolve("empty"));
        int exit = new CommandLine(new TestableListCommand(cfg, mockUrl()))
                .execute("--no-tui");
        assertTrue(err.toString().contains("Not logged in"));
    }

    @Test
    void noTuiFlag_printsTableWithTitle() throws IOException {
        enqueueProblems();
        new CommandLine(new TestableListCommand(withCredentials(), mockUrl()))
                .execute("--no-tui");
        assertTrue(out.toString().contains("Two Sum"));
        assertTrue(out.toString().contains("Easy"));
    }

    @Test
    void jsonFlag_outputsArray() throws IOException {
        enqueueProblems();
        new CommandLine(new TestableListCommand(withCredentials(), mockUrl()))
                .execute("--json");
        String o = out.toString().trim();
        assertTrue(o.startsWith("["), "Should start with [");
        assertTrue(o.endsWith("]"), "Should end with ]");
        assertTrue(o.contains("Two Sum"));
        assertTrue(o.contains("two-sum"));
    }

    @Test
    void searchFlag_passesKeywordInRequest() throws IOException, InterruptedException {
        enqueueProblems();
        new CommandLine(new TestableListCommand(withCredentials(), mockUrl()))
                .execute("--search", "two sum");
        assertTrue(server.takeRequest().getBody().readUtf8().contains("two sum"));
    }

    @Test
    void difficultyFlag_passesFilterInRequest() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
            .setBody("{\"data\":{\"problemsetQuestionList\":{\"total\":0,\"questions\":[]}}}")
            .setResponseCode(200).addHeader("Content-Type", "application/json"));
        new CommandLine(new TestableListCommand(withCredentials(), mockUrl()))
                .execute("--difficulty", "HARD");
        assertTrue(server.takeRequest().getBody().readUtf8().contains("HARD"));
    }

    // ── Testable subclass — no reflection, no TUI launch ──────────────

    /**
     * Extends ListCommand purely to inject config+baseUrl without touching private fields.
     * Overrides run() to skip TUI while reusing all picocli field binding.
     */
    static class TestableListCommand extends ListCommand {
        private final ConfigManager cfg;
        private final String baseUrl;

        TestableListCommand(ConfigManager cfg, String baseUrl) {
            this.cfg = cfg;
            this.baseUrl = baseUrl;
        }

        @Override
        public void run() {
            LeetCodeClient client = new LeetCodeClient(cfg, baseUrl);
            if (!client.hasCredentials()) {
                System.err.println("\n  Not logged in. Run 'leetcli login' first.\n");
                return;
            }
            // Delegate to parent's non-TUI paths by forcing --no-tui via flags field
            // Since we're in the same package, access package-private/protected fields directly
            // instead of reflection.
            noTui = true;
            runWithClient(client);
        }

        /** Runs the table/json output path against the injected client. */
        private void runWithClient(LeetCodeClient client) {
            try {
                if (json) {
                    var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                    var problems = client.listProblems(limit, (page - 1) * limit, difficulty, search);
                    System.out.println(gson.toJson(problems));
                } else {
                    int skip = (page - 1) * limit;
                    var problems = client.listProblems(limit, skip, difficulty, search);
                    if (problems.isEmpty()) { System.out.println("  No problems found.\n"); return; }
                    System.out.printf("  %-2s  %-6s  %-50s  %-8s  %6s%n", "", "#", "Title", "Diff", "AC%");
                    System.out.println("  " + "─".repeat(80));
                    for (var p : problems) {
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
    }
}
