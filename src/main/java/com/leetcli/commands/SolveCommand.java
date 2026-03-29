package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.Problem;
import com.leetcli.api.models.ProblemDetail;
import com.leetcli.config.ConfigManager;
import com.leetcli.tui.MainTUI;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

/**
 * Opens the multi-panel TUI for solving a specific problem.
 * Accepts problem ID (number) or slug (text).
 */
@Command(
    name = "solve",
    mixinStandardHelpOptions = true,
    description = "Open the TUI to solve a LeetCode problem (by ID or slug)"
)
public class SolveCommand implements Runnable {

    @Parameters(index = "0", description = "Problem ID (e.g., '1') or slug (e.g., 'two-sum')")
    private String problemRef;

    @Override
    public void run() {
        ConfigManager config = new ConfigManager();
        LeetCodeClient client = new LeetCodeClient(config);

        if (!client.hasCredentials()) {
            System.err.println("\n  ✗ Not logged in. Run 'leetcli login' first.\n");
            return;
        }

        try {
            String titleSlug;

            if (problemRef.matches("\\d+")) {
                // User passed a numeric ID — search for the problem
                System.out.println("\n  ⏳ Looking up problem #" + problemRef + "...\n");

                // Search with a broader result set to find an exact ID match
                List<Problem> problems = client.listProblems(50, 0, null, problemRef);
                String foundSlug = null;

                for (Problem p : problems) {
                    if (p.getFrontendQuestionId().equals(problemRef)) {
                        foundSlug = p.getTitleSlug();
                        break;
                    }
                }

                // If not found in the first batch, try a targeted search
                if (foundSlug == null) {
                    // Try fetching a wider range around the ID number
                    int id = Integer.parseInt(problemRef);
                    int skip = Math.max(0, id - 5);
                    problems = client.listProblems(20, skip, null, null);
                    for (Problem p : problems) {
                        if (p.getFrontendQuestionId().equals(problemRef)) {
                            foundSlug = p.getTitleSlug();
                            break;
                        }
                    }
                }

                if (foundSlug == null) {
                    System.err.println("  ✗ Problem #" + problemRef + " not found.\n");
                    return;
                }
                titleSlug = foundSlug;
            } else {
                titleSlug = problemRef;
            }

            System.out.println("  ⏳ Loading problem...\n");
            ProblemDetail problem = client.getProblemDetail(titleSlug);
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
            System.out.println("  Launching TUI...\n");

            // Launch the TUI
            MainTUI tui = new MainTUI(client, problem, config);
            tui.run();

        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
}
