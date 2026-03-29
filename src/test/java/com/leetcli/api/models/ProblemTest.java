package com.leetcli.api.models;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

class ProblemTest {

    private static final Gson GSON = new Gson();

    private Problem buildProblem(String status, String difficulty, double acRate,
                                  boolean paidOnly, String id, String title) {
        String json = String.format(
            "{\"status\":\"%s\",\"difficulty\":\"%s\",\"acRate\":%f," +
            "\"isPaidOnly\":%b,\"frontendQuestionId\":\"%s\",\"title\":\"%s\"," +
            "\"titleSlug\":\"%s\"}",
            status == null ? "null" : status, difficulty, acRate,
            paidOnly, id, title, title.toLowerCase().replace(" ", "-")
        );
        return GSON.fromJson(json, Problem.class);
    }

    @Test
    void getStatusIcon_ac() {
        Problem p = buildProblem("ac", "Easy", 55.0, false, "1", "Two Sum");
        assertEquals("✓", p.getStatusIcon());
    }

    @Test
    void getStatusIcon_notac() {
        Problem p = buildProblem("notac", "Medium", 40.0, false, "2", "Add Numbers");
        assertEquals("✗", p.getStatusIcon());
    }

    @Test
    void getStatusIcon_null_returnsSpace() {
        String json = "{\"status\":null,\"difficulty\":\"Hard\",\"acRate\":30.0," +
                "\"isPaidOnly\":false,\"frontendQuestionId\":\"3\",\"title\":\"Foo\",\"titleSlug\":\"foo\"}";
        Problem p = GSON.fromJson(json, Problem.class);
        assertEquals(" ", p.getStatusIcon());
    }

    @Test
    void getDifficultyColored_easy() {
        Problem p = buildProblem("ac", "Easy", 60.0, false, "1", "Two Sum");
        assertTrue(p.getDifficultyColored().contains("Easy"));
    }

    @Test
    void getDifficultyColored_medium() {
        Problem p = buildProblem(null, "Medium", 40.0, false, "2", "Merge");
        assertTrue(p.getDifficultyColored().contains("Medium"));
    }

    @Test
    void getDifficultyColored_hard() {
        Problem p = buildProblem(null, "Hard", 20.0, false, "3", "Hard One");
        assertTrue(p.getDifficultyColored().contains("Hard"));
    }

    @Test
    void isPaidOnly_true() {
        Problem p = buildProblem(null, "Easy", 50.0, true, "1000", "Premium");
        assertTrue(p.isPaidOnly());
    }

    @Test
    void getAcRate_returnsCorrectValue() {
        Problem p = buildProblem("ac", "Easy", 54.321, false, "1", "Two Sum");
        assertEquals(54.321, p.getAcRate(), 0.001);
    }

    @Test
    void getFrontendQuestionId_returnsId() {
        Problem p = buildProblem(null, "Easy", 55.0, false, "42", "Wildcard");
        assertEquals("42", p.getFrontendQuestionId());
    }

    @Test
    void getTitle_andSlug() {
        Problem p = buildProblem(null, "Medium", 45.0, false, "5", "Longest Palindrome");
        assertEquals("Longest Palindrome", p.getTitle());
        assertEquals("longest-palindrome", p.getTitleSlug());
    }
}
