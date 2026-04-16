package com.leetcli.tui;

import com.leetcli.api.models.ProblemDetail;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the full terminal frame buffer as a single string.
 * Uses explicit cursor positioning per row for robust resize handling.
 */
public final class Renderer {

    private Renderer() {}

    /**
     * Render the complete TUI frame.
     * Every row is explicitly positioned with \e[row;1H so the frame
     * renders correctly even if the terminal buffer is corrupted by resize wrapping.
     */
    public static String renderFrame(int W, int H, ProblemDetail problem,
                                     List<String> problemLines, EditorState codeEditor,
                                     EditorState testEditor, List<String> resultLines,
                                     int scrollProblem, int scrollResults,
                                     int activePanel, String currentLang, String statusMsg) {

        int leftW = W / 2;
        int rightW = W - leftW;
        int topH = Math.max(3, (int) (H * 0.62));
        int botH = Math.max(3, H - topH - 2);

        StringBuilder buf = new StringBuilder(W * H * 2);
        int row = 1;

        // ── Row 1: Title bar ──
        buf.append(cursorTo(row++));
        String diffBadge = Theme.difficultyBadge(problem.getDifficulty());
        String title = String.format(" LeetCLI \u2502 #%s %s \u2502%s\u2502 %s",
                problem.getQuestionFrontendId(),
                truncate(problem.getTitle(), 30),
                diffBadge,
                SyntaxHighlighter.getDisplayName(currentLang));
        buf.append(Theme.TITLE_BG).append(pad(title, W)).append(Theme.RESET);

        // ── Top panel borders ──
        List<String> wrapped = wrapLines(problemLines, leftW - 2);
        String editorLabel = Messages.get("panel.editor") + " [" + SyntaxHighlighter.getDisplayName(currentLang) + "]";

        buf.append(cursorTo(row++));
        buf.append(makeBorder(Messages.get("panel.problem"), Theme.CYAN, activePanel == 0, leftW));
        buf.append(makeBorder(editorLabel, Theme.GREEN, activePanel == 1, rightW));

        // ── Top panel content ──
        for (int r = 0; r < topH - 2; r++) {
            buf.append(cursorTo(row++));
            String lc = activePanel == 0 ? Theme.CYAN : Theme.DIM;
            String rc = activePanel == 1 ? Theme.GREEN : Theme.DIM;

            buf.append(lc).append("\u2502").append(Theme.RESET)
               .append(renderPlainLine(wrapped, scrollProblem + r, leftW - 2))
               .append(lc).append("\u2502").append(Theme.RESET);

            buf.append(rc).append("\u2502").append(Theme.RESET)
               .append(renderEditorLine(codeEditor, codeEditor.getScrollY() + r, rightW - 2, currentLang, activePanel == 1))
               .append(rc).append("\u2502").append(Theme.RESET);
        }

        // ── Top panel bottom borders ──
        buf.append(cursorTo(row++));
        buf.append(activePanel == 0 ? Theme.CYAN : Theme.DIM)
           .append("\u2514").append("\u2500".repeat(leftW - 2)).append("\u2518").append(Theme.RESET);
        buf.append(activePanel == 1 ? Theme.GREEN : Theme.DIM)
           .append("\u2514").append("\u2500".repeat(rightW - 2)).append("\u2518").append(Theme.RESET);

        // ── Bottom panel borders ──
        buf.append(cursorTo(row++));
        buf.append(makeBorder(Messages.get("panel.testcases"), Theme.YELLOW, activePanel == 2, leftW));
        buf.append(makeBorder(Messages.get("panel.results"), Theme.MAGENTA, activePanel == 3, rightW));

        // ── Bottom panel content ──
        for (int r = 0; r < botH - 2; r++) {
            buf.append(cursorTo(row++));
            String lc = activePanel == 2 ? Theme.YELLOW : Theme.DIM;
            String rc = activePanel == 3 ? Theme.MAGENTA : Theme.DIM;

            buf.append(lc).append("\u2502").append(Theme.RESET)
               .append(renderEditorLine(testEditor, testEditor.getScrollY() + r, leftW - 2, null, activePanel == 2))
               .append(lc).append("\u2502").append(Theme.RESET);

            buf.append(rc).append("\u2502").append(Theme.RESET)
               .append(renderPlainLine(resultLines, scrollResults + r, rightW - 2))
               .append(rc).append("\u2502").append(Theme.RESET);
        }

        // ── Bottom panel bottom borders ──
        buf.append(cursorTo(row++));
        buf.append(activePanel == 2 ? Theme.YELLOW : Theme.DIM)
           .append("\u2514").append("\u2500".repeat(leftW - 2)).append("\u2518").append(Theme.RESET);
        buf.append(activePanel == 3 ? Theme.MAGENTA : Theme.DIM)
           .append("\u2514").append("\u2500".repeat(rightW - 2)).append("\u2518").append(Theme.RESET);

        // ── Status bar ──
        buf.append(cursorTo(row));
        buf.append(Theme.STATUS_BG).append(pad(" " + statusMsg, W)).append(Theme.RESET);

        return buf.toString();
    }

    /** Explicit cursor positioning: \e[row;1H */
    private static String cursorTo(int row) {
        return Theme.ESC + row + ";1H";
    }

    /** Compute view heights for external scroll bounds. */
    public static int topViewH(int H) { return Math.max(3, (int) (H * 0.62)) - 2; }
    public static int botViewH(int H) { return Math.max(3, H - Math.max(3, (int) (H * 0.62)) - 2) - 2; }

    // ── Editor line rendering ──

    private static String renderEditorLine(EditorState editor, int y, int viewW,
                                           String lang, boolean isActive) {
        if (y < 0 || y >= editor.getLines().size()) {
            if (isActive && y == editor.getCurLine()) {
                StringBuilder sb = new StringBuilder();
                sb.append(Theme.INVERSE_ON).append(" ").append(Theme.INVERSE_OFF);
                sb.append(" ".repeat(Math.max(0, viewW - 1)));
                sb.append(Theme.RESET);
                return sb.toString();
            }
            return " ".repeat(viewW);
        }

        String raw = editor.getLine(y);
        int scrollX = editor.getScrollX();

        int[] fg = (lang != null && !raw.isEmpty())
                ? SyntaxHighlighter.highlight(raw, lang)
                : new int[raw.length()];

        StringBuilder sb = new StringBuilder();
        int curFg = 0;
        boolean curInv = false;

        for (int i = scrollX; i < scrollX + viewW; i++) {
            if (i >= raw.length()) {
                if (curInv) { sb.append(Theme.INVERSE_OFF); curInv = false; }
                if (isActive && y == editor.getCurLine() && i == editor.getCurCol()) {
                    sb.append(Theme.INVERSE_ON).append(" ").append(Theme.INVERSE_OFF);
                } else {
                    sb.append(" ");
                }
                continue;
            }

            boolean sel = editor.isSelected(y, i);
            if (sel != curInv) {
                curInv = sel;
                sb.append(curInv ? Theme.INVERSE_ON : Theme.INVERSE_OFF);
            }

            int color = fg[i];
            if (color != curFg) {
                curFg = color;
                switch (color) {
                    case SyntaxHighlighter.DEFAULT    -> sb.append(Theme.ESC).append(Theme.FG_RGB);
                    case SyntaxHighlighter.KEYWORD    -> sb.append(Theme.BLUE);
                    case SyntaxHighlighter.STRING     -> sb.append(Theme.YELLOW);
                    case SyntaxHighlighter.COMMENT    -> sb.append(Theme.GREY);
                    case SyntaxHighlighter.NUMBER     -> sb.append(Theme.RED);
                    case SyntaxHighlighter.TYPE       -> sb.append(Theme.TYPE_COLOR);
                    case SyntaxHighlighter.ANNOTATION -> sb.append(Theme.ANNOTATION_COLOR);
                    case SyntaxHighlighter.OPERATOR   -> sb.append(Theme.OPERATOR_COLOR);
                    case SyntaxHighlighter.BRACKET    -> sb.append(Theme.BRACKET_COLOR);
                }
            }

            boolean isCur = isActive && y == editor.getCurLine() && i == editor.getCurCol();
            if (isCur && !curInv) sb.append(Theme.INVERSE_ON);
            sb.append(raw.charAt(i));
            if (isCur && !curInv) sb.append(Theme.INVERSE_OFF);
        }

        sb.append(Theme.RESET);
        return sb.toString();
    }

    // ── Plain line rendering ──

    private static String renderPlainLine(List<String> lines, int y, int w) {
        String line = (y >= 0 && y < lines.size()) ? lines.get(y) : "";
        return pad(line, w);
    }

    // ── Text wrapping ──

    /** Soft-wrap lines at word boundaries to fit a given width. */
    public static List<String> wrapLines(List<String> lines, int width) {
        if (width <= 0) return lines;
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line.length() <= width) { result.add(line); continue; }
            int pos = 0;
            while (pos < line.length()) {
                int end = Math.min(pos + width, line.length());
                if (end < line.length()) {
                    int space = line.lastIndexOf(' ', end - 1);
                    if (space > pos) end = space + 1;
                }
                result.add(line.substring(pos, end));
                pos = end;
            }
        }
        return result;
    }

    // ── String utilities ──

    public static String pad(String s, int w) {
        if (w <= 0) return "";
        String visible = s.replaceAll("\u001b\\[[0-9;]*m", "");
        if (visible.length() >= w) return truncateVisible(s, w);
        return s + " ".repeat(w - visible.length());
    }

    private static String truncateVisible(String s, int max) {
        StringBuilder out = new StringBuilder();
        int vis = 0; boolean inEsc = false;
        for (char c : s.toCharArray()) {
            if (c == '\u001b') inEsc = true;
            if (!inEsc) { if (vis >= max) break; vis++; }
            out.append(c);
            if (inEsc && Character.isLetter(c)) inEsc = false;
        }
        return out.toString();
    }

    static String truncate(String s, int mx) {
        return s.length() <= mx ? s : s.substring(0, mx - 3) + "...";
    }

    static String makeBorder(String label, String color, boolean active, int w) {
        String marker = active ? "\u25cf " : "";
        String text = "\u2500 " + marker + label + " ";
        int dashLen = Math.max(0, w - 2 - text.length());
        return (active ? color : Theme.DIM) + "\u250c" + text + "\u2500".repeat(dashLen) + "\u2510" + Theme.RESET;
    }
}
