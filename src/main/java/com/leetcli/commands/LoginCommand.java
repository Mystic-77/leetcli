package com.leetcli.commands;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.config.ConfigManager;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Command(
    name = "login",
    description = "Connect your LeetCode account"
)
public class LoginCommand implements Runnable {

    @Option(names = {"--session", "-s"}, description = "LEETCODE_SESSION cookie (skip browser flow)")
    private String session;

    @Option(names = {"--csrf", "-c"}, description = "csrftoken cookie (skip browser flow)")
    private String csrfToken;

    @Override
    public void run() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────┐");
        System.out.println("  │   leetcode login                        │");
        System.out.println("  └─────────────────────────────────────────┘");
        System.out.println();

        // If both flags provided, skip the browser flow entirely
        if (session != null && !session.isBlank() && csrfToken != null && !csrfToken.isBlank()) {
            saveAndValidate(session, csrfToken);
            return;
        }

        // Try to auto-capture csrftoken via local callback server
        String capturedCsrf = tryCaptureCsrfToken();

        Scanner scanner = new Scanner(System.in);

        if (capturedCsrf != null) {
            csrfToken = capturedCsrf;
            System.out.println("  ✓ csrftoken captured automatically");
        } else {
            // Fallback: ask for both
            System.out.println("  Couldn't auto-capture. Manual fallback:");
            System.out.println("    1. Go to leetcode.com → F12 → Application → Cookies");
            System.out.println("    2. Copy csrftoken");
            System.out.println();
            System.out.print("  csrftoken: ");
            csrfToken = scanner.nextLine().trim();
        }

        System.out.println();
        System.out.println("  One more thing — LEETCODE_SESSION can't be read by scripts.");
        System.out.println("  In your browser: F12 → Application → Cookies → leetcode.com");
        System.out.println("  Copy the value of LEETCODE_SESSION");
        System.out.println();
        System.out.print("  LEETCODE_SESSION: ");
        session = scanner.nextLine().trim();

        if (session.isBlank() || csrfToken.isBlank()) {
            System.err.println("\n  ✗ Both values are required.");
            return;
        }

        saveAndValidate(session, csrfToken);
    }

    /**
     * Spins up a local HTTP server, opens the browser to leetcode.com,
     * and shows a JS snippet the user can paste in the console to auto-send
     * the csrftoken back to localhost.
     *
     * Returns the captured csrftoken, or null if it timed out.
     */
    private String tryCaptureCsrfToken() {
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        HttpServer server;
        int port;
        try {
            // Bind to any available port
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            port = server.getAddress().getPort();
        } catch (Exception e) {
            return null; // can't start server, fall back
        }

        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("csrf=")) {
                String value = URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
                captured.set(value);
            }
            String response = """
                    <html><body style="font-family:monospace;background:#1a1a1a;color:#a8cc8c;padding:40px">
                    <h2>✓ csrftoken captured!</h2>
                    <p>Go back to your terminal and paste your LEETCODE_SESSION.</p>
                    </body></html>
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
            latch.countDown();
        });

        server.start();

        System.out.println("  Opening leetcode.com in your browser...");
        openBrowser("https://leetcode.com/accounts/login/");

        System.out.println();
        System.out.println("  After logging in, open the browser console (F12 → Console)");
        System.out.println("  and paste this one-liner:");
        System.out.println();
        System.out.printf(
            "  fetch('http://localhost:%d/callback?csrf='+document.cookie.split('csrftoken=')[1]?.split(';')[0])%n",
            port
        );
        System.out.println();
        System.out.println("  Waiting (60s)... or press Ctrl+C to skip and enter manually.");
        System.out.println();

        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.stop(0);
        }

        return captured.get();
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {}

        // Fallback for headless / Linux without DISPLAY
        String[] cmds = {"xdg-open", "open", "sensible-browser"};
        for (String cmd : cmds) {
            try {
                new ProcessBuilder(cmd, url).start();
                return;
            } catch (Exception ignored) {}
        }

        System.out.println("  Couldn't open browser automatically.");
        System.out.println("  Open manually: " + url);
    }

    private void saveAndValidate(String sessionVal, String csrfVal) {
        ConfigManager config = new ConfigManager();
        config.set("leetcode_session", sessionVal);
        config.set("csrf_token", csrfVal);

        try {
            config.save();
        } catch (Exception e) {
            System.err.println("\n  ✗ Failed to save config: " + e.getMessage());
            return;
        }

        System.out.println("\n  ⏳ Validating...");

        LeetCodeClient client = new LeetCodeClient(config);
        try {
            JsonObject userStatus = client.validateSession();

            if (userStatus != null) {
                String username = userStatus.has("username")
                        ? userStatus.get("username").getAsString() : "unknown";
                boolean isPremium = userStatus.has("isPremium")
                        && userStatus.get("isPremium").getAsBoolean();

                config.set("username", username);
                config.save();

                System.out.println("  ✓ Connected!");
                System.out.println();
                System.out.printf("  Username : %s%n", username);
                System.out.printf("  Premium  : %s%n", isPremium ? "Yes" : "No");
                System.out.println();
                System.out.println("  Run:  leetcode list");
                System.out.println("        leetcode solve two-sum");
                System.out.println();
            } else {
                System.err.println("  ✗ Cookies invalid or expired. Log into leetcode.com and try again.");
            }
        } catch (Exception e) {
            System.err.println("  ✗ Network error: " + e.getMessage());
        }
    }
}
