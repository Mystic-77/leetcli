package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.config.ConfigManager;
import com.google.gson.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Scanner;

/**
 * Login command — configures LeetCode authentication.
 * 
 * Users must provide their LEETCODE_SESSION and csrftoken cookies
 * from their browser (DevTools → Application → Cookies → leetcode.com).
 */
@Command(
    name = "login",
    description = "Authenticate with LeetCode using browser cookies"
)
public class LoginCommand implements Runnable {

    @Option(names = {"--session", "-s"}, description = "LEETCODE_SESSION cookie value")
    private String session;

    @Option(names = {"--csrf", "-c"}, description = "csrftoken cookie value")
    private String csrfToken;

    @Override
    public void run() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║         LeetCLI — Login              ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        // Prompt for cookies if not provided via flags
        if (session == null || session.isBlank()) {
            System.out.println("  To authenticate, you need cookies from your browser.");
            System.out.println("  Steps:");
            System.out.println("    1. Log into leetcode.com in your browser");
            System.out.println("    2. Open DevTools (F12) → Application → Cookies");
            System.out.println("    3. Find 'leetcode.com' and copy the values below");
            System.out.println();
            System.out.print("  LEETCODE_SESSION: ");
            session = scanner.nextLine().trim();
        }

        if (csrfToken == null || csrfToken.isBlank()) {
            System.out.print("  csrftoken: ");
            csrfToken = scanner.nextLine().trim();
        }

        if (session.isBlank() || csrfToken.isBlank()) {
            System.err.println("\n  ✗ Both LEETCODE_SESSION and csrftoken are required.");
            return;
        }

        // Save credentials
        ConfigManager config = new ConfigManager();
        config.set("leetcode_session", session);
        config.set("csrf_token", csrfToken);

        try {
            config.save();
        } catch (Exception e) {
            System.err.println("\n  ✗ Failed to save config: " + e.getMessage());
            return;
        }

        System.out.println("\n  ⏳ Validating session...");

        // Validate by making a test API call
        LeetCodeClient client = new LeetCodeClient(config);
        try {
            JsonObject userStatus = client.validateSession();

            if (userStatus != null) {
                String username = userStatus.has("username")
                        ? userStatus.get("username").getAsString() : "unknown";
                boolean isPremium = userStatus.has("isPremium")
                        && userStatus.get("isPremium").getAsBoolean();

                // Save the username for later use
                config.set("username", username);
                config.save();

                System.out.println("  ✓ Authenticated successfully!");
                System.out.println();
                System.out.println("  ┌──────────────────────────────────┐");
                System.out.printf("  │  Username:  %-20s │%n", username);
                System.out.printf("  │  Premium:   %-20s │%n", isPremium ? "Yes ⭐" : "No");
                System.out.println("  └──────────────────────────────────┘");
                System.out.println();
                System.out.println("  Config saved to: " + ConfigManager.getConfigDir());
                System.out.println("  Run 'leetcli whoami' to check your session anytime.");
                System.out.println();
            } else {
                System.err.println("  ✗ Authentication failed!");
                System.err.println("    The cookies may be expired or invalid.");
                System.err.println("    Please log into leetcode.com again and copy fresh cookies.");
            }
        } catch (Exception e) {
            System.err.println("  ✗ Error connecting to LeetCode: " + e.getMessage());
            System.err.println("    Check your internet connection and try again.");
        }
    }
}
