package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.config.ConfigManager;
import com.leetcli.tui.ProblemBrowser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * List and filter LeetCode problems.
 */
@Command(
    name = "list",
    description = "Browse and filter LeetCode problems"
)
public class ListCommand implements Runnable {

    @Option(names = {"-l", "--limit"}, description = "Number of problems to show (default: 20)",
            defaultValue = "20")
    private int limit;

    @Option(names = {"-p", "--page"}, description = "Page number (default: 1)",
            defaultValue = "1")
    private int page;

    @Option(names = {"-d", "--difficulty"}, description = "Filter by difficulty: EASY, MEDIUM, HARD")
    private String difficulty;

    @Option(names = {"-s", "--search"}, description = "Search by keyword")
    private String search;

    @Override
    public void run() {
        ConfigManager config = new ConfigManager();
        LeetCodeClient client = new LeetCodeClient(config);

        if (!client.hasCredentials()) {
            System.err.println("\n  ✗ Not logged in. Run 'leetcli login' first.\n");
            return;
        }

        try {
            ProblemBrowser browser = new ProblemBrowser(client, config);
            browser.setInitialState(difficulty, search, page);
            browser.run();
        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage() + "\n");
        }
    }
}
