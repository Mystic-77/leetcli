package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import picocli.CommandLine.Command;

/**
 * WhoAmI command — displays current session info and user stats.
 */
@Command(
    name = "whoami",
    description = "Display current authenticated user and stats"
)
public class WhoAmICommand implements Runnable {

    @Override
    public void run() {
        ConfigManager config = new ConfigManager();
        LeetCodeClient client = new LeetCodeClient(config);

        if (!client.hasCredentials()) {
            System.err.println("\n  ✗ Not logged in. Run 'leetcli login' first.\n");
            return;
        }

        System.out.println("\n  ⏳ Fetching your profile...\n");

        try {
            // Validate session first
            JsonObject userStatus = client.validateSession();

            if (userStatus == null) {
                System.err.println("  ✗ Session expired! Run 'leetcli login' to re-authenticate.\n");
                return;
            }

            String username = userStatus.get("username").getAsString();
            boolean isPremium = userStatus.has("isPremium")
                    && userStatus.get("isPremium").getAsBoolean();

            // Fetch detailed stats
            JsonObject statsResponse = client.getUserStats(username);

            System.out.println("  ╔══════════════════════════════════════╗");
            System.out.println("  ║          Your LeetCode Profile       ║");
            System.out.println("  ╠══════════════════════════════════════╣");
            System.out.printf("  ║  Username:  %-24s ║%n", username);
            System.out.printf("  ║  Premium:   %-24s ║%n", isPremium ? "Yes ⭐" : "No");

            if (statsResponse != null && statsResponse.has("data")) {
                JsonObject data = statsResponse.getAsJsonObject("data");
                if (data.has("matchedUser") && !data.get("matchedUser").isJsonNull()) {
                    JsonObject user = data.getAsJsonObject("matchedUser");

                    // Profile info
                    if (user.has("profile") && !user.get("profile").isJsonNull()) {
                        JsonObject profile = user.getAsJsonObject("profile");
                        if (profile.has("ranking") && !profile.get("ranking").isJsonNull()) {
                            System.out.printf("  ║  Ranking:   %-24s ║%n",
                                    "#" + profile.get("ranking").getAsInt());
                        }
                    }

                    // Submission stats
                    if (user.has("submitStatsGlobal") && !user.get("submitStatsGlobal").isJsonNull()) {
                        JsonObject submitStats = user.getAsJsonObject("submitStatsGlobal");
                        if (submitStats.has("acSubmissionNum")) {
                            JsonArray acStats = submitStats.getAsJsonArray("acSubmissionNum");

                            System.out.println("  ╠══════════════════════════════════════╣");
                            System.out.println("  ║        Problems Solved               ║");
                            System.out.println("  ╠══════════════════════════════════════╣");

                            for (var element : acStats) {
                                JsonObject stat = element.getAsJsonObject();
                                String difficulty = stat.get("difficulty").getAsString();
                                int count = stat.get("count").getAsInt();

                                String icon = switch (difficulty) {
                                    case "All" -> "📊";
                                    case "Easy" -> "🟢";
                                    case "Medium" -> "🟡";
                                    case "Hard" -> "🔴";
                                    default -> "  ";
                                };

                                System.out.printf("  ║  %s %-8s  %-21d ║%n",
                                        icon, difficulty, count);
                            }
                        }
                    }
                }
            }

            System.out.println("  ╚══════════════════════════════════════╝");
            System.out.println();

        } catch (Exception e) {
            System.err.println("  ✗ Error: " + e.getMessage());
            System.err.println("    Try 'leetcli login' to refresh your session.\n");
        }
    }
}
