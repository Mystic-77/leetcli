package com.leetcli.api.models;

import java.util.List;

/**
 * Represents a problem in a list view (summary info).
 */
public class Problem {
    private double acRate;
    private String difficulty;
    private String frontendQuestionId;
    private boolean isPaidOnly;
    private String title;
    private String titleSlug;
    private String status; // "ac", "notac", or null (not attempted)
    private List<TopicTag> topicTags;

    public double getAcRate() { return acRate; }
    public String getDifficulty() { return difficulty; }
    public String getFrontendQuestionId() { return frontendQuestionId; }
    public boolean isPaidOnly() { return isPaidOnly; }
    public String getTitle() { return title; }
    public String getTitleSlug() { return titleSlug; }
    public String getStatus() { return status; }
    public List<TopicTag> getTopicTags() { return topicTags; }

    public String getStatusIcon() {
        if ("ac".equals(status)) return "✓";
        if ("notac".equals(status)) return "✗";
        return " ";
    }

    public String getDifficultyColored() {
        return switch (difficulty) {
            case "Easy" -> "🟢 Easy";
            case "Medium" -> "🟡 Medium";
            case "Hard" -> "🔴 Hard";
            default -> difficulty;
        };
    }

    public static class TopicTag {
        private String name;
        private String slug;
        public String getName() { return name; }
        public String getSlug() { return slug; }
    }
}
