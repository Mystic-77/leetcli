package com.leetcli.api;

/**
 * Centralized GraphQL query strings for LeetCode's API.
 */
public final class GraphQLQueries {

    private GraphQLQueries() {}

    /**
     * Fetch a paginated, filterable list of problems.
     */
    public static final String PROBLEM_LIST = """
            query problemsetQuestionList(
                $categorySlug: String,
                $limit: Int,
                $skip: Int,
                $filters: QuestionListFilterInput
            ) {
                problemsetQuestionList: questionList(
                    categorySlug: $categorySlug,
                    limit: $limit,
                    skip: $skip,
                    filters: $filters
                ) {
                    total: totalNum
                    questions: data {
                        acRate
                        difficulty
                        frontendQuestionId: questionFrontendId
                        isPaidOnly
                        title
                        titleSlug
                        status
                        topicTags {
                            name
                            slug
                        }
                    }
                }
            }
            """;

    /**
     * Fetch detailed info for a single problem by slug.
     */
    public static final String PROBLEM_DETAIL = """
            query getQuestionDetail($titleSlug: String!) {
                question(titleSlug: $titleSlug) {
                    questionId
                    questionFrontendId
                    title
                    titleSlug
                    content
                    difficulty
                    likes
                    dislikes
                    categoryTitle
                    isPaidOnly
                    codeSnippets {
                        lang
                        langSlug
                        code
                    }
                    sampleTestCase
                    exampleTestcaseList
                    metaData
                    topicTags {
                        name
                        slug
                    }
                    hints
                    status
                }
            }
            """;

    /**
     * Validate session — fetch current user status.
     */
    public static final String USER_STATUS = """
            query globalData {
                userStatus {
                    userId
                    username
                    realName
                    avatar
                    isSignedIn
                    isPremium
                    activeSessionId
                }
            }
            """;

    /**
     * Get user profile with solve stats.
     */
    public static final String USER_PROFILE = """
            query getUserProfile($username: String!) {
                matchedUser(username: $username) {
                    username
                    profile {
                        realName
                        ranking
                        reputation
                        starRating
                    }
                    submitStatsGlobal {
                        acSubmissionNum {
                            difficulty
                            count
                            submissions
                        }
                    }
                }
            }
            """;
}
