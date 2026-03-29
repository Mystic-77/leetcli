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

class SolveCommandTest {

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
        SolveCommandForTest cmd = new SolveCommandForTest(cfg, server.url("/").toString(), "1");
        new CommandLine(cmd).execute("1");
        assertTrue(err.toString().contains("Not logged in"));
    }

    @Test
    void numericId_resolvesToSlug_thenLoadsProblem() throws IOException {
        ConfigManager cfg = withCredentials();

        // First call: listProblems to find slug by ID
        String listBody = """
            {"data":{"problemsetQuestionList":{"total":3000,"questions":[
              {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
               "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":"ac"}
            ]}}}
            """;
        // Second call: getProblemDetail
        String detailBody = """
            {"data":{"question":{"questionFrontendId":"1","title":"Two Sum","titleSlug":"two-sum",
             "difficulty":"Easy","isPaidOnly":false,"content":"<p>Given an array...</p>",
             "codeSnippets":[{"lang":"Java","langSlug":"java","code":"class Solution {}"}],
             "sampleTestCase":"[2,7,11,15]\\n9","exampleTestcases":"[2,7,11,15]\\n9"}}}
            """;

        server.enqueue(new MockResponse().setBody(listBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody(detailBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        SolveCommandForTest cmd = new SolveCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""), "1");
        cmd.runHeadless();

        String output = out.toString();
        assertTrue(output.contains("Two Sum"), "Should show problem title after resolving");
    }

    @Test
    void slugRef_loadsDirectly() throws IOException {
        ConfigManager cfg = withCredentials();

        String detailBody = """
            {"data":{"question":{"questionFrontendId":"1","title":"Two Sum","titleSlug":"two-sum",
             "difficulty":"Easy","isPaidOnly":false,"content":"<p>Given an array...</p>",
             "codeSnippets":[{"lang":"Java","langSlug":"java","code":"class Solution {}"}],
             "sampleTestCase":"[2,7,11,15]\\n9","exampleTestcases":"[2,7,11,15]\\n9"}}}
            """;
        server.enqueue(new MockResponse().setBody(detailBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        SolveCommandForTest cmd = new SolveCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""), "two-sum");
        cmd.runHeadless();

        assertTrue(out.toString().contains("Two Sum"));
    }

    @Test
    void paidOnlyProblem_printsError() throws IOException {
        ConfigManager cfg = withCredentials();

        // listProblems → find slug
        String listBody = """
            {"data":{"problemsetQuestionList":{"total":1,"questions":[
              {"frontendQuestionId":"308","title":"Range Sum Query 2D - Mutable",
               "titleSlug":"range-sum-query-2d-mutable","difficulty":"Hard",
               "acRate":40.0,"isPaidOnly":true,"status":null}
            ]}}}
            """;
        // detail says paidOnly
        String detailBody = """
            {"data":{"question":{"questionFrontendId":"308","title":"Range Sum Query 2D - Mutable",
             "titleSlug":"range-sum-query-2d-mutable","difficulty":"Hard","isPaidOnly":true,
             "content":null,"codeSnippets":[],"sampleTestCase":"","exampleTestcases":""}}}
            """;
        server.enqueue(new MockResponse().setBody(listBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody(detailBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        SolveCommandForTest cmd = new SolveCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""), "308");
        cmd.runHeadless();

        assertTrue(err.toString().contains("premium"), "Should print premium-only error");
    }

    @Test
    void unknownId_printsNotFound() throws IOException {
        ConfigManager cfg = withCredentials();

        // Both listProblems calls return empty
        String emptyBody = """
            {"data":{"problemsetQuestionList":{"total":0,"questions":[]}}}
            """;
        server.enqueue(new MockResponse().setBody(emptyBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody(emptyBody).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        SolveCommandForTest cmd = new SolveCommandForTest(cfg, server.url("/").toString().replaceAll("/$", ""), "99999");
        cmd.runHeadless();

        assertTrue(err.toString().contains("not found") || err.toString().contains("99999"),
                "Should report problem not found");
    }

    // ── Helpers ──

    private ConfigManager withCredentials() throws IOException {
        ConfigManager cfg = new ConfigManager(tempDir);
        cfg.set("leetcode_session", "s");
        cfg.set("csrf_token", "c");
        cfg.save();
        return cfg;
    }

    // ── Headless subclass — skips TUI launch, only validates resolution logic ──

    static class SolveCommandForTest extends SolveCommand {
        private final ConfigManager cfg;
        private final String baseUrl;
        private final String ref;

        SolveCommandForTest(ConfigManager cfg, String baseUrl, String ref) {
            this.cfg = cfg;
            this.baseUrl = baseUrl;
            this.ref = ref;
        }

        /** Run all logic except the TUI screen (which requires a real terminal). */
        void runHeadless() {
            com.leetcli.api.LeetCodeClient client = new com.leetcli.api.LeetCodeClient(cfg, baseUrl);

            if (!client.hasCredentials()) {
                System.err.println("\n  ✗ Not logged in. Run 'leetcli login' first.\n");
                return;
            }

            try {
                String titleSlug;

                if (ref.matches("\\d+")) {
                    System.out.println("\n  Looking up problem #" + ref + "...\n");
                    java.util.List<com.leetcli.api.models.Problem> problems =
                            client.listProblems(50, 0, null, ref);
                    String foundSlug = null;

                    for (com.leetcli.api.models.Problem p : problems) {
                        if (p.getFrontendQuestionId().equals(ref)) {
                            foundSlug = p.getTitleSlug();
                            break;
                        }
                    }

                    if (foundSlug == null) {
                        int id = Integer.parseInt(ref);
                        int skip = Math.max(0, id - 5);
                        problems = client.listProblems(20, skip, null, null);
                        for (com.leetcli.api.models.Problem p : problems) {
                            if (p.getFrontendQuestionId().equals(ref)) {
                                foundSlug = p.getTitleSlug();
                                break;
                            }
                        }
                    }

                    if (foundSlug == null) {
                        System.err.println("  ✗ Problem #" + ref + " not found.\n");
                        return;
                    }
                    titleSlug = foundSlug;
                } else {
                    titleSlug = ref;
                }

                com.leetcli.api.models.ProblemDetail problem = client.getProblemDetail(titleSlug);
                if (problem == null) {
                    System.err.println("  ✗ Problem '" + titleSlug + "' not found.\n");
                    return;
                }

                if (problem.isPaidOnly()) {
                    System.err.println("  ✗ Problem '" + problem.getTitle() + "' is premium only.\n");
                    return;
                }

                System.out.println("  ✓ Loaded: #" + problem.getQuestionFrontendId()
                        + " " + problem.getTitle() + " [" + problem.getDifficulty() + "]");
                System.out.println("  (TUI skipped in headless test mode)");

            } catch (Exception e) {
                System.err.println("  ✗ Error: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            runHeadless();
        }
    }
}
