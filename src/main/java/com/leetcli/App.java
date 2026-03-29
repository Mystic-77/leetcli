package com.leetcli;

import com.leetcli.commands.ListCommand;
import com.leetcli.commands.LoginCommand;
import com.leetcli.commands.SolveCommand;
import com.leetcli.commands.WhoAmICommand;
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
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║          LeetCLI v1.0                ║");
        System.out.println("  ║   Terminal-based LeetCode Client     ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Commands:");
        System.out.println("    login    Authenticate with LeetCode");
        System.out.println("    whoami   Show your profile and stats");
        System.out.println("    list     Browse and filter problems");
        System.out.println("    solve    Open TUI to solve a problem");
        System.out.println();
        System.out.println("  Use --help for more details.");
        System.out.println();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
