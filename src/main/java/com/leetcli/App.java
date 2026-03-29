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
    version = "LeetCLI 1.0-SNAPSHOT",
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
        // No args → go straight into the problem browser.
        // If not logged in yet, redirect to login first.
        ConfigManager config = new ConfigManager();
        if (config.get("leetcode_session") == null || config.get("leetcode_session").isBlank()) {
            System.out.println("\n  Not logged in. Run: leetcli login\n");
            return;
        }
        new CommandLine(new ListCommand()).execute();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
