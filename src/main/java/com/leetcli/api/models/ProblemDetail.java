package com.leetcli.api.models;

import java.util.List;

/**
 * Detailed problem info including description, code stubs, and test cases.
 */
public class ProblemDetail {
    private String questionId;
    private String questionFrontendId;
    private String title;
    private String titleSlug;
    private String content;          // HTML content
    private String difficulty;
    private int likes;
    private int dislikes;
    private String categoryTitle;
    private boolean isPaidOnly;
    private List<CodeSnippet> codeSnippets;
    private String sampleTestCase;
    private List<String> exampleTestcaseList;
    private String metaData;
    private List<Problem.TopicTag> topicTags;
    private List<String> hints;
    private String status;

    // Getters
    public String getQuestionId() { return questionId; }
    public String getQuestionFrontendId() { return questionFrontendId; }
    public String getTitle() { return title; }
    public String getTitleSlug() { return titleSlug; }
    public String getContent() { return content; }
    public String getDifficulty() { return difficulty; }
    public int getLikes() { return likes; }
    public int getDislikes() { return dislikes; }
    public String getCategoryTitle() { return categoryTitle; }
    public boolean isPaidOnly() { return isPaidOnly; }
    public List<CodeSnippet> getCodeSnippets() { return codeSnippets; }
    public String getSampleTestCase() { return sampleTestCase; }
    public List<String> getExampleTestcaseList() { return exampleTestcaseList; }
    public String getMetaData() { return metaData; }
    public List<Problem.TopicTag> getTopicTags() { return topicTags; }
    public List<String> getHints() { return hints; }
    public String getStatus() { return status; }

    /**
     * Get the code snippet for a specific language.
     */
    public CodeSnippet getCodeSnippetForLang(String langSlug) {
        if (codeSnippets == null) return null;
        return codeSnippets.stream()
                .filter(s -> s.getLangSlug().equals(langSlug))
                .findFirst()
                .orElse(null);
    }

    public static class CodeSnippet {
        private String lang;
        private String langSlug;
        private String code;

        public String getLang() { return lang; }
        public String getLangSlug() { return langSlug; }
        public String getCode() { return code; }
    }
}
