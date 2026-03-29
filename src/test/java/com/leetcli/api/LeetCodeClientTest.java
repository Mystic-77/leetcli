package com.leetcli.api;

import com.leetcli.api.models.Problem;
import com.leetcli.config.ConfigManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeetCodeClientTest {

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private ConfigManager config;
    private LeetCodeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new ConfigManager(tempDir);
        config.set("leetcode_session", "test-session");
        config.set("csrf_token", "test-csrf");

        client = new LeetCodeClient(config, server.url("/").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // ── hasCredentials ───────────────────────────────────────────────

    @Test
    void hasCredentials_trueWhenBothSet() {
        assertTrue(client.hasCredentials());
    }

    @Test
    void hasCredentials_falseWhenSessionMissing() {
        ConfigManager cfg = new ConfigManager(tempDir.resolve("empty"));
        LeetCodeClient c = new LeetCodeClient(cfg, server.url("/").toString());
        assertFalse(c.hasCredentials());
    }

    // ── validateSession ──────────────────────────────────────────────

    @Test
    void validateSession_returnsUserStatus_whenSignedIn() throws IOException {
        String body = """
            {"data":{"userStatus":{"isSignedIn":true,"username":"tester","isPremium":false}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        var result = client.validateSession();
        assertNotNull(result);
        assertEquals("tester", result.get("username").getAsString());
        assertTrue(result.get("isSignedIn").getAsBoolean());
    }

    @Test
    void validateSession_returnsNull_whenNotSignedIn() throws IOException {
        String body = """
            {"data":{"userStatus":{"isSignedIn":false,"username":""}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        assertNull(client.validateSession());
    }

    // ── listProblems ─────────────────────────────────────────────────

    @Test
    void listProblems_parsesProblemsFromResponse() throws IOException {
        String body = """
            {"data":{"problemsetQuestionList":{"total":3000,"questions":[
              {"frontendQuestionId":"1","title":"Two Sum","titleSlug":"two-sum",
               "difficulty":"Easy","acRate":54.3,"isPaidOnly":false,"status":"ac"},
              {"frontendQuestionId":"2","title":"Add Two Numbers","titleSlug":"add-two-numbers",
               "difficulty":"Medium","acRate":41.2,"isPaidOnly":false,"status":null}
            ]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        List<Problem> problems = client.listProblems(20, 0, null, null);
        assertEquals(2, problems.size());
        assertEquals("1", problems.get(0).getFrontendQuestionId());
        assertEquals("Two Sum", problems.get(0).getTitle());
        assertEquals("Easy", problems.get(0).getDifficulty());
        assertEquals("✓", problems.get(0).getStatusIcon());
        assertEquals("2", problems.get(1).getFrontendQuestionId());
        assertEquals(" ", problems.get(1).getStatusIcon());
    }

    @Test
    void listProblems_returnsEmpty_onNullList() throws IOException {
        String body = """
            {"data":{"problemsetQuestionList":null}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        List<Problem> problems = client.listProblems(20, 0, null, null);
        assertTrue(problems.isEmpty());
    }

    @Test
    void listProblems_sendsDifficultyFilter_inRequest() throws IOException, InterruptedException {
        String body = """
            {"data":{"problemsetQuestionList":{"total":800,"questions":[]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        client.listProblems(20, 0, "Easy", null);

        RecordedRequest req = server.takeRequest();
        String reqBody = req.getBody().readUtf8();
        assertTrue(reqBody.contains("EASY"), "Request should include difficulty filter");
    }

    @Test
    void listProblems_throwsOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThrows(IOException.class, () -> client.listProblems(20, 0, null, null));
    }

    // ── getTotalProblems ─────────────────────────────────────────────

    @Test
    void getTotalProblems_returnsTotal() throws IOException {
        String body = """
            {"data":{"problemsetQuestionList":{"total":3247,"questions":[]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        int total = client.getTotalProblems(null);
        assertEquals(3247, total);
    }

    // ── Auth headers ─────────────────────────────────────────────────

    @Test
    void requests_includeAuthHeaders() throws IOException, InterruptedException {
        String body = """
            {"data":{"problemsetQuestionList":{"total":0,"questions":[]}}}
            """;
        server.enqueue(new MockResponse().setBody(body).setResponseCode(200)
                .addHeader("Content-Type", "application/json"));

        client.listProblems(1, 0, null, null);

        RecordedRequest req = server.takeRequest();
        String cookie = req.getHeader("Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("LEETCODE_SESSION=test-session"), "Cookie should include session");
        assertTrue(cookie.contains("csrftoken=test-csrf"), "Cookie should include csrf");
        assertEquals("test-csrf", req.getHeader("X-Csrftoken"));
    }
}
