package com.leetcli;

import com.leetcli.commands.ListCommand;
import com.leetcli.commands.LoginCommand;
import com.leetcli.commands.SolveCommand;
import com.leetcli.commands.WhoAmICommand;
import com.leetcli.config.ConfigManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "leetcli",
    mixinStandardHelpOptions = true,
    version = "LeetCLI 1.0",
    description = "Terminal-based LeetCode client",
    subcommands = {
        LoginCommand.class,
        WhoAmICommand.class,
        ListCommand.class,
        SolveCommand.class
    }
)
public class App implements Runnable {

    @Override
    public void run() {
        ConfigManager config = new ConfigManager();
        boolean loggedIn = config.has("leetcode_session") && config.has("csrf_token");

        if (!loggedIn) {
            printWelcome();
            // Run login wizard inline — hands control to LoginCommand
            new CommandLine(new LoginCommand()).execute();

            // Re-check — if login succeeded, drop into browser
            config = new ConfigManager();
            if (!config.has("leetcode_session")) return;  // login failed/cancelled
        }

        new CommandLine(new ListCommand()).execute();
    }

    private static void printWelcome() {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────┐");
        System.out.println("  │            Welcome to leetcli               │");
        System.out.println("  │      Terminal-based LeetCode client         │");
        System.out.println("  └─────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  Looks like your first time here.");
        System.out.println("  Let's connect your LeetCode account.");
        System.out.println();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
