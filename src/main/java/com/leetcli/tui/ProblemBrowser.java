package com.leetcli.tui;

import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.Problem;
import com.leetcli.api.models.ProblemDetail;
import com.leetcli.config.ConfigManager;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive problem browser — arrow keys to navigate, Enter to open,
 * / to search, d to cycle difficulty, n/p to page, q to quit.
 */
public class ProblemBrowser {

    private static final int PAGE_SIZE = 50;
    private static final String[] DIFFICULTIES = {"", "Easy", "Medium", "Hard"};
    private static final String[] DIFF_LABELS   = {"All", "Easy", "Medium", "Hard"};

    private final LeetCodeClient client;
    private final ConfigManager config;

    private Terminal terminal;
    private PrintWriter writer;
    private int W, H;

    private List<Problem> problems = new ArrayList<>();
    private int cursor    = 0;
    private int diffIndex = 0;   // 0=All 1=Easy 2=Medium 3=Hard
    private int page      = 0;   // 0-based
    private int totalProblems = 0;

    private boolean searchMode = false;
    private StringBuilder searchBuffer = new StringBuilder();
    private String activeSearch = "";

    private String statusMsg = "";

    public ProblemBrowser(LeetCodeClient client, ConfigManager config) {
        this.client = client;
        this.config = config;
    }

    public void run() throws IOException {
        openTerminal();
        try {
            loadProblems();
            render();
            loop();
        } finally {
            exitAltBuf();
            terminal.close();
        }
    }

    // ── Terminal lifecycle ──────────────────────────────────────────

    private void openTerminal() throws IOException {
        terminal = TerminalBuilder.builder().system(true).build();
        terminal.enterRawMode();
        writer = terminal.writer();
        W = terminal.getWidth();
        H = terminal.getHeight();
        writer.print(Theme.ALT_BUF_ON + Theme.HIDE_CURSOR + Theme.AUTOWRAP_OFF);
        writer.flush();
    }

    private void exitAltBuf() {
        writer.print(Theme.ALT_BUF_OFF + Theme.SHOW_CURSOR + Theme.AUTOWRAP_ON);
        writer.flush();
    }

    // ── Main loop ───────────────────────────────────────────────────

    private void loop() throws IOException {
        while (true) {
            W = terminal.getWidth();
            H = terminal.getHeight();

            InputHandler.KeyEvent ev = InputHandler.read(terminal.reader());

            if (searchMode) {
                if (!handleSearchInput(ev)) render();
                // handleSearchInput returns true only when we need to reload (handled inside)
            } else {
                if (handleAction(ev)) break;
                render();
            }
        }
    }

    /** Returns false always (render is handled by caller); reloads + re-renders on Enter. */
    private boolean handleSearchInput(InputHandler.KeyEvent ev) {
        switch (ev.action()) {
            case QUIT -> {
                searchMode = false;
                searchBuffer.setLength(0);
            }
            case ENTER -> {
                searchMode = false;
                activeSearch = searchBuffer.toString().trim();
                searchBuffer.setLength(0);
                page = 0;
                cursor = 0;
                loadProblems();
            }
            case BACKSPACE -> {
                if (!searchBuffer.isEmpty())
                    searchBuffer.deleteCharAt(searchBuffer.length() - 1);
            }
            case DELETE -> searchBuffer.setLength(0);
            case CHAR -> searchBuffer.append(ev.ch());
            default -> {}
        }
        render();
        return false;
    }

    /** Returns true if the browser should exit. */
    private boolean handleAction(InputHandler.KeyEvent ev) {
        switch (ev.action()) {
            case QUIT        -> { return true; }
            case ARROW_UP    -> moveCursor(-1);
            case ARROW_DOWN  -> moveCursor(1);
            case HOME        -> cursor = 0;
            case END         -> cursor = Math.max(0, problems.size() - 1);
            case ENTER       -> openSelected();
            case CHAR -> {
                switch (ev.ch()) {
                    case 'q', 'Q' -> { return true; }
                    case 'j'      -> moveCursor(1);
                    case 'k'      -> moveCursor(-1);
                    case 'g'      -> cursor = 0;
                    case 'G'      -> cursor = Math.max(0, problems.size() - 1);
                    case '/'      -> { searchMode = true; searchBuffer.setLength(0); }
                    case 'd'      -> {
                        diffIndex = (diffIndex + 1) % 4;
                        page = 0; cursor = 0;
                        loadProblems();
                    }
                    case 'n'      -> { page++; cursor = 0; loadProblems(); }
                    case 'p'      -> { if (page > 0) { page--; cursor = 0; loadProblems(); } }
                    case 'r'      -> loadProblems();  // refresh
                    default       -> {}
                }
            }
            default -> {}
        }
        return false;
    }

    private void moveCursor(int delta) {
        int next = cursor + delta;
        if (next < 0) {
            if (page > 0) { page--; loadProblems(); cursor = problems.size() - 1; }
        } else if (next >= problems.size()) {
            int maxPage = Math.max(0, (totalProblems - 1) / PAGE_SIZE);
            if (page < maxPage) { page++; loadProblems(); cursor = 0; }
        } else {
            cursor = next;
        }
    }

    // ── Open selected problem ───────────────────────────────────────

    private void openSelected() {
        if (problems.isEmpty()) return;
        Problem p = problems.get(cursor);
        if (p.isPaidOnly()) {
            statusMsg = "Premium only";
            return;
        }

        // Hand off terminal to solve TUI
        exitAltBuf();
        try {
            terminal.close();
        } catch (IOException ignored) {}

        try {
            ProblemDetail detail = client.getProblemDetail(p.getTitleSlug());
            System.out.printf("\n  Loading #%s %s...%n%n",
                    p.getFrontendQuestionId(), p.getTitle());
            MainTUI tui = new MainTUI(client, detail, config);
            tui.run();
        } catch (Exception e) {
            System.err.println("\n  Error: " + e.getMessage());
            System.err.println("  Press Enter to return to browser...");
            try { System.in.read(); } catch (IOException ignored) {}
        }

        // Rebuild browser terminal
        try {
            openTerminal();
        } catch (IOException e) {
            // Terminal rebuild failed — nothing we can do
        }
    }

    // ── Data loading ────────────────────────────────────────────────

    private void loadProblems() {
        statusMsg = "Loading...";
        render();
        try {
            String diff = DIFFICULTIES[diffIndex];
            String search = activeSearch.isEmpty() ? null : activeSearch;
            problems = client.listProblems(PAGE_SIZE, page * PAGE_SIZE,
                    diff.isEmpty() ? null : diff, search);
            totalProblems = client.getTotalProblems(diff.isEmpty() ? null : diff);
            if (cursor >= problems.size()) cursor = Math.max(0, problems.size() - 1);
            statusMsg = "";
        } catch (Exception e) {
            statusMsg = "Network error — r to retry";
            problems = new ArrayList<>();
        }
    }

    // ── Rendering ───────────────────────────────────────────────────

    private void render() {
        if (writer == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(Theme.CLEAR);

        // ── Title bar ──
        String diffLabel = DIFF_LABELS[diffIndex];
        String searchDisplay = searchMode
                ? ("/" + searchBuffer + "█")
                : (activeSearch.isEmpty() ? "" : ("/" + activeSearch));
        String titleBar = String.format("  leetcli   %-8s  %-30s  %d problems",
                diffLabel, searchDisplay, totalProblems);
        sb.append(Theme.TITLE_BG).append(padRight(titleBar, W)).append(Theme.RESET).append('\n');

        // ── Column headers ──
        int titleW = Math.max(10, W - 36);
        sb.append(Theme.DIM)
          .append(String.format("  %-2s  %-6s  %-" + titleW + "s  %-8s  %6s",
                  "", "#", "Title", "Diff", "AC%"))
          .append(Theme.RESET).append('\n');
        sb.append(Theme.DIM).append("─".repeat(W)).append(Theme.RESET).append('\n');

        // ── Problem rows ──
        int listH = H - 5;
        int viewStart = Math.max(0, cursor - listH + 1);
        if (cursor < viewStart) viewStart = cursor;

        int rendered = 0;
        for (int i = viewStart; i < problems.size() && rendered < listH; i++, rendered++) {
            Problem p = problems.get(i);
            boolean sel = (i == cursor);

            String statusIcon = p.getStatusIcon();
            String id   = p.getFrontendQuestionId();
            String title = truncate(p.isPaidOnly() ? "🔒 " + p.getTitle() : p.getTitle(), titleW);
            String diff  = p.getDifficulty();
            String ac    = String.format("%.1f%%", p.getAcRate());

            String diffColor = switch (diff) {
                case "Easy"   -> Theme.GREEN;
                case "Medium" -> Theme.YELLOW;
                case "Hard"   -> Theme.RED;
                default       -> Theme.DIM;
            };
            String statusColor = switch (statusIcon) {
                case "✓" -> Theme.GREEN;
                case "✗" -> Theme.RED;
                default  -> Theme.DIM;
            };

            if (sel) sb.append(Theme.INVERSE_ON);

            sb.append("  ")
              .append(statusColor).append(String.format("%-2s", statusIcon))
              .append(Theme.RESET).append(sel ? Theme.INVERSE_ON : "")
              .append("  ").append(String.format("%-6s", id))
              .append("  ").append(String.format("%-" + titleW + "s", title))
              .append("  ").append(diffColor).append(String.format("%-8s", diff))
              .append(Theme.RESET).append(sel ? Theme.INVERSE_ON : "")
              .append("  ").append(String.format("%6s", ac));

            if (sel) sb.append(Theme.INVERSE_OFF);
            sb.append('\n');
        }

        // Fill blank rows
        for (int i = rendered; i < listH; i++) sb.append('\n');

        // ── Footer ──
        sb.append(Theme.DIM).append("─".repeat(W)).append(Theme.RESET).append('\n');
        int totalPages = Math.max(1, (totalProblems + PAGE_SIZE - 1) / PAGE_SIZE);
        String footer;
        if (searchMode) {
            footer = "  SEARCH: " + searchBuffer + "█   Esc = cancel   Enter = go";
        } else {
            String extra = statusMsg.isEmpty() ? "" : "  │  " + statusMsg;
            footer = String.format(
                    "  ↑↓/jk move  Enter open  / search  d difficulty  n/p page %d/%d  r refresh  q quit%s",
                    page + 1, totalPages, extra);
        }
        sb.append(Theme.STATUS_BG).append(padRight(footer, W)).append(Theme.RESET);

        writer.print(sb);
        writer.flush();
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static String padRight(String s, int width) {
        // Strip ANSI when measuring — rough: just use raw length for padding
        int visibleLen = s.replaceAll("\u001b\\[[^m]*m", "").length();
        if (visibleLen >= width) return s;
        return s + " ".repeat(width - visibleLen);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
