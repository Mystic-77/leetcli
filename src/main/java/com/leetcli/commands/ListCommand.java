package com.leetcli.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.Problem;
import com.leetcli.config.ConfigManager;
import com.leetcli.tui.ProblemBrowser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * Browse LeetCode problems.
 * - No flags  → interactive TUI browser (arrow keys, Enter to open)
 * - With flags → static table output (scriptable / pipe-friendly)
 */
@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "Browse and filter LeetCode problems"
)
public class ListCommand implements Runnable {

    @Option(names = {"-l", "--limit"}, description = "Table mode: rows to show (default: 20)",
            defaultValue = "20")
    int limit;

    @Option(names = {"-p", "--page"}, description = "Table mode: page number (default: 1)",
            defaultValue = "1")
    int page;

    @Option(names = {"-d", "--difficulty"}, description = "Filter: EASY, MEDIUM, HARD")
    String difficulty;

    @Option(names = {"-s", "--search"}, description = "Search by keyword")
    String search;

    @Option(names = {"--no-tui"}, description = "Force table output even without other flags")
    boolean noTui;

    @Option(names = {"--json"}, description = "Output as JSON array (agent-friendly)")
    boolean json;

    @Override
    public void run() {
        ConfigManager config = new ConfigManager();
        LeetCodeClient client = new LeetCodeClient(config);

        if (!client.hasCredentials()) {
            System.err.println("\n  Not logged in. Run 'leetcli login' first.\n");
            return;
        }

        if (json) {
            printJson(client);
            return;
        }

        boolean wantsTable = noTui || difficulty != null || search != null || page > 1;

        if (!wantsTable) {
            try {
                new ProblemBrowser(client, config).run();
            } catch (Exception e) {
                System.err.println("  TUI error: " + e.getMessage());
                System.err.println("  Falling back to table output...\n");
                printTable(client);
            }
        } else {
            printTable(client);
        }
    }

    private void printJson(LeetCodeClient client) {
        try {
            int skip = (page - 1) * limit;
            List<Problem> problems = client.listProblems(limit, skip, difficulty, search);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(problems));
        } catch (Exception e) {
            System.err.println("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void printTable(LeetCodeClient client) {
        System.out.println("\n  Fetching problems...\n");
        try {
            int skip = (page - 1) * limit;
            List<Problem> problems = client.listProblems(limit, skip, difficulty, search);

            if (problems.isEmpty()) {
                System.out.println("  No problems found.\n");
                return;
            }

            System.out.printf("  %-2s  %-6s  %-50s  %-8s  %6s%n", "", "#", "Title", "Diff", "AC%");
            System.out.println("  " + "─".repeat(80));

            for (Problem p : problems) {
                String title = p.getTitle();
                if (title.length() > 48) title = title.substring(0, 45) + "...";
                if (p.isPaidOnly()) title = "🔒 " + title;

                String diff = switch (p.getDifficulty()) {
                    case "Easy"   -> "🟢 Easy  ";
                    case "Medium" -> "🟡 Medium";
                    case "Hard"   -> "🔴 Hard  ";
                    default -> p.getDifficulty();
                };

                System.out.printf("  [%s] %-6s  %-50s  %-10s  %5.1f%%%n",
                        p.getStatusIcon(),
                        p.getFrontendQuestionId(),
                        title, diff,
                        p.getAcRate());
            }

            System.out.println("  " + "─".repeat(80));
            System.out.printf("  Page %d  |  %d problems shown%n%n", page, problems.size());

        } catch (Exception e) {
            System.err.println("  Error: " + e.getMessage() + "\n");
        }
    }
}
