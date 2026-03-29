package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.Problem;
import com.leetcli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

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

        System.out.println("\n  ⏳ Fetching problems...\n");

        try {
            int skip = (page - 1) * limit;
            List<Problem> problems = client.listProblems(limit, skip, difficulty, search);

            if (problems.isEmpty()) {
                System.out.println("  No problems found matching your criteria.\n");
                return;
            }

            // Header
            System.out.printf("  %-4s %-6s %-50s %-12s %8s%n",
                    "  ", "ID", "Title", "Difficulty", "AC Rate");
            System.out.println("  " + "─".repeat(84));

            // Problem rows
            for (Problem p : problems) {
                String statusIcon = p.getStatusIcon();
                String id = p.getFrontendQuestionId();
                String title = p.getTitle();
                if (title.length() > 48) {
                    title = title.substring(0, 45) + "...";
                }
                String diff = p.getDifficulty();
                String acRate = String.format("%.1f%%", p.getAcRate());

                // Lock icon for premium
                if (p.isPaidOnly()) {
                    title = "🔒 " + title;
                }

                String diffDisplay = switch (diff) {
                    case "Easy" -> "🟢 Easy  ";
                    case "Medium" -> "🟡 Medium";
                    case "Hard" -> "🔴 Hard  ";
                    default -> diff;
                };

                System.out.printf("  [%s] %-6s %-50s %-12s %8s%n",
                        statusIcon, id, title, diffDisplay, acRate);
            }

            System.out.println("  " + "─".repeat(84));
            System.out.printf("  Page %d  |  Showing %d problems", page, problems.size());
            if (difficulty != null) System.out.printf("  |  Filter: %s", difficulty);
            if (search != null) System.out.printf("  |  Search: \"%s\"", search);
            System.out.println("\n");
            System.out.println("  💡 Use 'leetcli solve <problem-slug>' to start solving!");
            System.out.println("     e.g., leetcli solve two-sum\n");

        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage() + "\n");
        }
    }
}
