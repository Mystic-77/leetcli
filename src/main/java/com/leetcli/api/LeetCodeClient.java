package com.leetcli.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.leetcli.api.models.Problem;
import com.leetcli.api.models.ProblemDetail;
import com.leetcli.config.ConfigManager;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for LeetCode's GraphQL and REST APIs.
 * Handles authentication via session cookies and CSRF tokens.
 */
public class LeetCodeClient {

    private final String baseUrl;
    private final String graphqlUrl;
    private final OkHttpClient httpClient;
    private final ConfigManager configManager;

    private static final String DEFAULT_BASE_URL = "https://leetcode.com";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();

    public LeetCodeClient(ConfigManager configManager) {
        this(configManager, DEFAULT_BASE_URL);
    }

    public LeetCodeClient(ConfigManager configManager, String baseUrl) {
        this.configManager = configManager;
        this.baseUrl = baseUrl;
        
        if (!baseUrl.startsWith("https://") && !baseUrl.contains("localhost") 
                && !baseUrl.contains("127.0.0.1") && !baseUrl.contains("[::1]") 
                && !baseUrl.matches("http://[^/]+:\\d+.*")) {
            throw new IllegalArgumentException("BASE_URL must use HTTPS: " + baseUrl);
        }
        
        this.graphqlUrl = baseUrl + "/graphql/";
        
        CertificatePinner pinner = new CertificatePinner.Builder()
                .add("leetcode.com", "sha256/7Y3ExpJ1tcp3IVH+pNTe1T43fkah7S1F0/mMGtbpdfI=")
                .add("leetcode.com", "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=")
                .add("leetcode.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
                .build();
                
        this.httpClient = new OkHttpClient.Builder()
                .certificatePinner(pinner)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * Check if we have stored credentials.
     */
    public boolean hasCredentials() {
        return configManager.has("leetcode_session") && configManager.has("csrf_token");
    }

    // ─────────────────────────────────────────────────────────
    //  Authentication
    // ─────────────────────────────────────────────────────────

    /**
     * Validate current session by fetching the authenticated user's profile.
     */
    public JsonObject validateSession() throws IOException {
        JsonObject response = graphqlRequest(GraphQLQueries.USER_STATUS, null);
        if (response != null && response.has("data")) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("userStatus")) {
                JsonObject userStatus = data.getAsJsonObject("userStatus");
                if (userStatus.has("isSignedIn") && userStatus.get("isSignedIn").getAsBoolean()) {
                    return userStatus;
                }
            }
        }
        return null;
    }

    /**
     * Fetch user profile stats.
     */
    public JsonObject getUserStats(String username) throws IOException {
        Map<String, String> variables = Map.of("username", username);
        return graphqlRequest(GraphQLQueries.USER_PROFILE, variables);
    }

    // ─────────────────────────────────────────────────────────
    //  Problem Listing
    // ─────────────────────────────────────────────────────────

    private static final java.util.Set<String> VALID_DIFFICULTIES = java.util.Set.of("EASY", "MEDIUM", "HARD");

    private Map<String, Object> buildFilters(String difficulty, String searchKeyword) {
        Map<String, Object> filters = new HashMap<>();
        if (difficulty != null && !difficulty.isBlank()) {
            String upper = difficulty.toUpperCase();
            if (!VALID_DIFFICULTIES.contains(upper)) {
                throw new IllegalArgumentException("Invalid difficulty: " + difficulty);
            }
            filters.put("difficulty", upper);
        }
        if (searchKeyword != null && !searchKeyword.isBlank()) {
            if (searchKeyword.length() > 100) {
                throw new IllegalArgumentException("Search keyword too long (max 100 chars)");
            }
            filters.put("searchKeywords", searchKeyword);
        }
        return filters;
    }

    /**
     * Fetch a paginated list of problems with optional filters.
     */
    public List<Problem> listProblems(int limit, int skip, String difficulty, String searchKeyword)
            throws IOException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("categorySlug", "");
        variables.put("limit", limit);
        variables.put("skip", skip);
        variables.put("filters", buildFilters(difficulty, searchKeyword));

        JsonObject response = graphqlRequest(GraphQLQueries.PROBLEM_LIST, variables);

        List<Problem> problems = new ArrayList<>();
        if (response != null && response.has("data")) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("problemsetQuestionList") && !data.get("problemsetQuestionList").isJsonNull()) {
                JsonObject qList = data.getAsJsonObject("problemsetQuestionList");
                if (qList.has("questions")) {
                    JsonArray questions = qList.getAsJsonArray("questions");
                    for (var q : questions) {
                        problems.add(GSON.fromJson(q, Problem.class));
                    }
                }
            }
        }
        return problems;
    }

    /**
     * Get total number of problems (with optional filter).
     */
    public int getTotalProblems(String difficulty) throws IOException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("categorySlug", "");
        variables.put("limit", 1);
        variables.put("skip", 0);
        variables.put("filters", buildFilters(difficulty, null));

        JsonObject response = graphqlRequest(GraphQLQueries.PROBLEM_LIST, variables);
        if (response != null && response.has("data")) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("problemsetQuestionList") && !data.get("problemsetQuestionList").isJsonNull()) {
                JsonObject qList = data.getAsJsonObject("problemsetQuestionList");
                if (qList.has("total")) {
                    return qList.get("total").getAsInt();
                }
            }
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────
    //  Problem Details
    // ─────────────────────────────────────────────────────────

    /**
     * Fetch detailed info for a specific problem.
     */
    public ProblemDetail getProblemDetail(String titleSlug) throws IOException {
        Map<String, String> variables = Map.of("titleSlug", titleSlug);
        JsonObject response = graphqlRequest(GraphQLQueries.PROBLEM_DETAIL, variables);

        if (response != null && response.has("data")) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("question") && !data.get("question").isJsonNull()) {
                return GSON.fromJson(data.getAsJsonObject("question"), ProblemDetail.class);
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────
    //  Code Execution (Run / Submit)
    // ─────────────────────────────────────────────────────────

    /**
     * Run code against test cases (interpret_solution).
     * Returns the interpret_id for polling.
     */
    public String runCode(String titleSlug, String questionId, String lang,
                          String typedCode, String testCases) throws IOException {
        String url = baseUrl + "/problems/" + titleSlug + "/interpret_solution/";

        JsonObject body = new JsonObject();
        body.addProperty("lang", lang);
        body.addProperty("question_id", questionId);
        body.addProperty("typed_code", typedCode);
        body.addProperty("data_input", testCases);

        Request request = buildAuthenticatedRequest(url)
                .post(RequestBody.create(GSON.toJson(body), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Run code failed: HTTP " + response.code());
            }
            JsonObject result = GSON.fromJson(response.body().string(), JsonObject.class);
            if (result.has("interpret_id")) {
                return result.get("interpret_id").getAsString();
            }
            throw new IOException("No interpret_id in response: " + result);
        }
    }

    /**
     * Submit code for full test case evaluation.
     * Returns the submission_id for polling.
     */
    public String submitCode(String titleSlug, String questionId, String lang,
                             String typedCode) throws IOException {
        String url = baseUrl + "/problems/" + titleSlug + "/submit/";

        JsonObject body = new JsonObject();
        body.addProperty("lang", lang);
        body.addProperty("question_id", questionId);
        body.addProperty("typed_code", typedCode);

        Request request = buildAuthenticatedRequest(url)
                .post(RequestBody.create(GSON.toJson(body), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Submit failed: HTTP " + response.code());
            }
            JsonObject result = GSON.fromJson(response.body().string(), JsonObject.class);
            if (result.has("submission_id")) {
                return result.get("submission_id").getAsString();
            }
            throw new IOException("No submission_id in response: " + result);
        }
    }

    /**
     * Poll for run/submit result status.
     * Returns the full result object once complete.
     */
    public JsonObject checkResult(String submissionId) throws IOException {
        String url = baseUrl + "/submissions/detail/" + submissionId + "/check/";

        Request request = buildAuthenticatedRequest(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Check result failed: HTTP " + response.code());
            }
            return GSON.fromJson(response.body().string(), JsonObject.class);
        }
    }

    /**
     * Poll for result until it's ready (with timeout).
     */
    public JsonObject waitForResult(String submissionId, int maxWaitSeconds) throws IOException {
        long start = System.currentTimeMillis();
        long timeout = maxWaitSeconds * 1000L;

        while (System.currentTimeMillis() - start < timeout) {
            JsonObject result = checkResult(submissionId);
            if (result.has("state")) {
                String state = result.get("state").getAsString();
                if ("SUCCESS".equals(state)) {
                    return result;
                }
                if ("FAILURE".equals(state) || "ERROR".equals(state)) {
                    return result;
                }
            }
            // Wait before polling again
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for result");
            }
        }
        throw new IOException("Timed out waiting for result after " + maxWaitSeconds + "s");
    }

    // ─────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Send a GraphQL request to LeetCode.
     */
    public JsonObject graphqlRequest(String query, Object variables) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("query", query);
        if (variables != null) {
            body.add("variables", GSON.toJsonTree(variables));
        }

        Request request = buildAuthenticatedRequest(graphqlUrl)
                .post(RequestBody.create(GSON.toJson(body), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LeetCode API returned HTTP " + response.code());
            }
            String responseBody = response.body().string();
            return GSON.fromJson(responseBody, JsonObject.class);
        }
    }

    /**
     * Build a request with the required auth headers and cookies.
     */
    private Request.Builder buildAuthenticatedRequest(String url) {
        String session = configManager.get("leetcode_session");
        String csrfToken = configManager.get("csrf_token");

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Referer", baseUrl + "/")
                .header("Origin", baseUrl)
                .header("User-Agent", "LeetCLI/1.0");

        if (session != null && csrfToken != null) {
            builder.header("Cookie", "LEETCODE_SESSION=" + session + "; csrftoken=" + csrfToken)
                   .header("X-Csrftoken", csrfToken);
        }

        return builder;
    }
}
