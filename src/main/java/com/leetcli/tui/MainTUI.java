package com.leetcli.tui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.ProblemDetail;
import com.leetcli.config.ConfigManager;
import com.leetcli.util.HtmlToText;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-panel TUI using JLine3 + ANSI escape codes.
 * Renders directly inside the terminal like tmux/btop.
 */
public class MainTUI {

    private final LeetCodeClient client;
    private final ProblemDetail problem;
    private final ConfigManager config;

    private Terminal terminal;
    private PrintWriter writer;
    private int W, H; // terminal width/height

    // Panel content
    private List<String> problemLines;
    private List<String> editorLines;
    private List<String> testCaseLines;
    private List<String> resultLines;

    // Scroll offsets (Y axis)
    private int[] scroll = {0, 0, 0, 0};

    // Editor offsets (X axis)
    private int scrollX = 0;

    // Active panel: 0=problem, 1=editor, 2=tests, 3=results
    private int active = 1;

    // Editor cursor
    private int curLine = 0, curCol = 0;
    
    // Editor text selection
    private int anchorLine = -1, anchorCol = -1;

    private String statusMsg = "";
    private volatile boolean running = true;

    // ANSI helpers
    private static final String ESC = "\u001b[";
    // Everforest Dark (Hard) TrueColors
    private static final String BG_RGB = "48;2;45;53;59m";
    private static final String FG_RGB = "38;2;211;198;170m";
    
    // Globals reset everything back to Everforest
    private static final String RESET = ESC + "0m" + ESC + BG_RGB + ESC + FG_RGB;
    private static final String HIDE_CURSOR = ESC + "?25l";
    private static final String SHOW_CURSOR = ESC + "?25h";
    private static final String ALT_BUF_ON = ESC + "?1049h";
    private static final String ALT_BUF_OFF = ESC + "?1049l";
    private static final String CLEAR = ESC + BG_RGB + ESC + "2J";

    // Everforest Palette
    private static final String DIM = ESC + "38;2;133;146;137m";
    private static final String CYAN = ESC + "38;2;131;192;146m";
    private static final String BLUE = ESC + "38;2;127;187;179m";
    private static final String GREEN = ESC + "38;2;166;180;101m";
    private static final String YELLOW = ESC + "38;2;219;188;127m";
    private static final String MAGENTA = ESC + "38;2;214;153;182m";
    private static final String RED = ESC + "38;2;230;126;128m";
    private static final String GREY = ESC + "38;2;122;132;120m";

    // Reverse Video / Specific elements
    private static final String TITLE_BG = ESC + "48;2;127;187;179m" + ESC + "38;2;45;53;59m"; // Blue Bg, Dark text
    private static final String STATUS_BG = ESC + "48;2;166;180;101m" + ESC + "38;2;45;53;59m"; // Green Bg, Dark text
    
    // Badge Backgrounds
    private static final String BADGE_GREEN = ESC + "48;2;166;180;101m" + ESC + "38;2;45;53;59m"; // Green bg, dark fg
    private static final String BADGE_YELLOW = ESC + "48;2;219;188;127m" + ESC + "38;2;45;53;59m"; // Yellow bg, dark fg
    private static final String BADGE_RED = ESC + "48;2;230;126;128m" + ESC + "38;2;45;53;59m"; // Red bg, dark fg

    private static final String[] KEYWORDS = {
        "public", "private", "protected", "class", "interface", "enum",
        "void", "int", "boolean", "double", "float", "char", "long",
        "if", "else", "for", "while", "do", "switch", "case", "return",
        "new", "this", "super", "try", "catch", "finally", "throw", "throws",
        "static", "final", "abstract", "import", "package", "String", "List"
    };

    public MainTUI(LeetCodeClient client, ProblemDetail problem, ConfigManager config) {
        this.client = client;
        this.problem = problem;
        this.config = config;
    }

    public void run() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .jansi(true)
                .build();
        terminal.enterRawMode();
        writer = terminal.writer();

        // Switch to alternate screen buffer
        writer.print(ALT_BUF_ON + HIDE_CURSOR + CLEAR);
        writer.flush();

        try {
            initPanels();

            while (running) {
                W = terminal.getWidth();
                H = terminal.getHeight();
                if (W < 40 || H < 10) {
                    writer.print(ESC + "1;1H" + CLEAR);
                    writer.print("Terminal too small. Resize to at least 40x10.");
                    writer.flush();
                    handleInput();
                    continue;
                }
                render();
                handleInput();
            }
        } finally {
            writer.print(SHOW_CURSOR + RESET + ALT_BUF_OFF);
            writer.flush();
            terminal.close();
        }
    }

    private void initPanels() {
        String desc = HtmlToText.convert(problem.getContent());
        problemLines = new ArrayList<>(List.of(desc.replace("\t", "    ").split("\n")));

        String code = getCodeStub();
        editorLines = new ArrayList<>(List.of(code.replace("\t", "    ").split("\n", -1)));

        String tc = problem.getSampleTestCase() != null ? problem.getSampleTestCase() : "";
        testCaseLines = new ArrayList<>(List.of(tc.replace("\t", "    ").split("\n", -1)));

        resultLines = new ArrayList<>(List.of(
                "Shortcuts:",
                "",
                " F5        Run code",
                " F6        Submit solution",
                " F7        Load from file",
                " Ctrl+S    Save to file",
                " Ctrl+Arr  Switch panel",
                " Shift+Arr Select text in Editor",
                " Esc       Quit",
                "",
                "Tab = indent in editor."
        ));

        switchPanel(1);
    }

    // ═══════════════════════════════════════════════════════
    //  RENDERING
    // ═══════════════════════════════════════════════════════

    private void render() {
        int leftW = W / 2;
        int rightW = W - leftW;
        int topH = Math.max(3, (int) (H * 0.62));
        int botH = Math.max(3, H - topH - 2); 

        StringBuilder buf = new StringBuilder(W * H * 2);
        buf.append(ESC).append("1;1H");

        // ── Row 1: Title bar ──
        String diffBadge = switch (problem.getDifficulty()) {
            case "Easy" -> BADGE_GREEN + " Easy " + TITLE_BG;
            case "Medium" -> BADGE_YELLOW + " Medium " + TITLE_BG;
            case "Hard" -> BADGE_RED + " Hard " + TITLE_BG;
            default -> " " + problem.getDifficulty() + " ";
        };
        String title = String.format(" LeetCLI │ #%s %s │%s",
                problem.getQuestionFrontendId(), truncate(problem.getTitle(), 30),
                diffBadge);
        buf.append(TITLE_BG).append(pad(title, W)).append(RESET);

        // ── Top half: Problem (left) | Editor (right) ──
        String[] lLabel = makeBorder("Problem", CYAN, active == 0, leftW);
        String[] rLabel = makeBorder("Editor [Java]", GREEN, active == 1, rightW);
        buf.append(lLabel[0]).append(rLabel[0]);

        for (int r = 0; r < topH - 2; r++) {
            String lc = active == 0 ? CYAN : DIM;
            String rc = active == 1 ? GREEN : DIM;
            
            buf.append(lc).append("│").append(RESET)
               .append(renderBasicLine(problemLines, scroll[0] + r, leftW - 2))
               .append(lc).append("│").append(RESET);
               
            buf.append(rc).append("│").append(RESET)
               .append(renderEditorLine(scroll[1] + r, rightW - 2))
               .append(rc).append("│").append(RESET);
        }

        buf.append(active == 0 ? CYAN : DIM).append("└").append("─".repeat(leftW - 2)).append("┘").append(RESET);
        buf.append(active == 1 ? GREEN : DIM).append("└").append("─".repeat(rightW - 2)).append("┘").append(RESET);

        // ── Bottom half: Tests (left) | Results (right) ──
        String[] blLabel = makeBorder("Test Cases", YELLOW, active == 2, leftW);
        String[] brLabel = makeBorder("Results", MAGENTA, active == 3, rightW);
        buf.append(blLabel[0]).append(brLabel[0]);

        for (int r = 0; r < botH - 2; r++) {
            String lc = active == 2 ? YELLOW : DIM;
            String rc = active == 3 ? MAGENTA : DIM;
            
            buf.append(lc).append("│").append(RESET)
               .append(renderBasicLine(testCaseLines, scroll[2] + r, leftW - 2))
               .append(lc).append("│").append(RESET);
               
            buf.append(rc).append("│").append(RESET)
               .append(renderBasicLine(resultLines, scroll[3] + r, rightW - 2))
               .append(rc).append("│").append(RESET);
        }

        buf.append(active == 2 ? YELLOW : DIM).append("└").append("─".repeat(leftW - 2)).append("┘").append(RESET);
        buf.append(active == 3 ? MAGENTA : DIM).append("└").append("─".repeat(rightW - 2)).append("┘").append(RESET);

        // ── Status bar ──
        buf.append(STATUS_BG).append(pad(" " + statusMsg, W)).append(RESET);

        writer.print(buf);
        writer.flush();
    }

    private String[] makeBorder(String label, String color, boolean act, int w) {
        String marker = act ? "● " : "";
        String text = "─ " + marker + label + " ";
        int dashLen = Math.max(0, w - 2 - text.length());
        return new String[]{ (act ? color : DIM) + "┌" + text + "─".repeat(dashLen) + "┐" + RESET };
    }

    private String getLine(List<String> lines, int idx) {
        if (idx >= 0 && idx < lines.size()) return lines.get(idx);
        return "";
    }

    private String renderBasicLine(List<String> lines, int y, int w) {
        return pad(getLine(lines, y), w);
    }

    private String renderEditorLine(int y, int viewW) {
        if (y < 0 || y >= editorLines.size()) return " ".repeat(viewW);
        String raw = editorLines.get(y);
        
        // 1. Tokenize for Syntax Highlighting
        int[] fg = new int[raw.length()]; // 0=def, 1=cyan, 2=yellow(str), 3=gray(comment), 4=red(num)
        boolean inStr = false, inComment = false;
        
        for (int i = 0; i < raw.length(); i++) {
            if (inComment) { fg[i] = 3; continue; }
            if (raw.startsWith("//", i)) { inComment = true; fg[i] = 3; if (i+1 < raw.length()) fg[i+1] = 3; i++; continue; }
            if (raw.charAt(i) == '"') {
                if (inStr) { fg[i] = 2; inStr = false; continue; }
                else { inStr = true; fg[i] = 2; continue; }
            }
            if (inStr) { fg[i] = 2; continue; }
            if (Character.isDigit(raw.charAt(i))) { fg[i] = 4; }
        }
        
        for (String kw : KEYWORDS) {
            int idx = -1;
            while ((idx = raw.indexOf(kw, idx + 1)) != -1) {
                boolean startOk = idx == 0 || !Character.isLetterOrDigit(raw.charAt(idx - 1));
                boolean endOk = idx + kw.length() == raw.length() || !Character.isLetterOrDigit(raw.charAt(idx + kw.length()));
                if (startOk && endOk) {
                    for (int i = 0; i < kw.length(); i++) if (fg[idx+i] == 0) fg[idx+i] = 1;
                }
            }
        }

        // 2. Generate exactly viewW characters
        StringBuilder sb = new StringBuilder();
        int curFg = 0;
        boolean curInv = false;
        
        for (int i = scrollX; i < scrollX + viewW; i++) {
            if (i >= raw.length()) {
                if (curInv) { sb.append("\u001b[27m"); curInv = false; }
                sb.append(" ");
                continue;
            }
            
            boolean sel = isSelected(y, i);
            if (sel != curInv) {
                curInv = sel;
                sb.append(curInv ? "\u001b[7m" : "\u001b[27m");
            }
            
            int color = fg[i];
            if (color != curFg) {
                curFg = color;
                switch(color) {
                    case 0 -> sb.append(ESC).append(FG_RGB);
                    case 1 -> sb.append(BLUE); // keywords
                    case 2 -> sb.append(YELLOW); // strings
                    case 3 -> sb.append(GREY); // comments
                    case 4 -> sb.append(RED); // numbers
                }
            }
            
            // Draw Cursor (if active)
            boolean isCur = (active == 1 && y == curLine && i == curCol);
            if (isCur && !curInv) sb.append("\u001b[7m");
            sb.append(raw.charAt(i));
            if (isCur && !curInv) sb.append("\u001b[27m");
        }
        sb.append("\u001b[0m"); // Safety reset
        return sb.toString();
    }

    private boolean isSelected(int y, int x) {
        if (anchorLine == -1) return false;
        int sy = curLine, sx = curCol;
        int ey = anchorLine, ex = anchorCol;
        if (sy > ey || (sy == ey && sx > ex)) {
            sy = anchorLine; sx = anchorCol;
            ey = curLine; ex = curCol;
        }
        if (y < sy || y > ey) return false;
        if (sy == ey) return x >= sx && x < ex;
        if (y == sy) return x >= sx;
        if (y == ey) return x < ex;
        return true;
    }

    private String pad(String s, int w) {
        if (w <= 0) return "";
        String visible = s.replaceAll("\u001b\\[[0-9;]*m", "");
        if (visible.length() >= w) return truncateVisible(s, w);
        return s + " ".repeat(w - visible.length());
    }

    private String truncateVisible(String s, int max) {
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
    private String truncate(String s, int mx) { return s.length() <= mx ? s : s.substring(0, mx - 3) + "..."; }

    // ═══════════════════════════════════════════════════════
    //  INPUT HANDLING
    // ═══════════════════════════════════════════════════════

    private void handleInput() throws IOException {
        NonBlockingReader reader = terminal.reader();
        int c = reader.read(); // Blocks until a key is pressed!
        if (c == -1 || c == -2) return;

        if (c == 27) { 
            int c2 = reader.read(200);
            if (c2 == -1 || c2 == -2) { running = false; return; }
            if (c2 == 'O') {
                int c3 = reader.read(); // BLOCKING READ ensures trailing character 'A' is absorbed!
                if (c3 > 0) processEscape(c2, String.valueOf((char) c3));
            } else if (c2 == '[') {
                StringBuilder seq = new StringBuilder();
                while (true) {
                    int b = reader.read(); // BLOCKING READ removes staggered letter leakage!
                    if (b <= 0) break;
                    seq.append((char) b);
                    if (b >= 0x40 && b <= 0x7E) break; // Terminal byte reaches end of CSI
                }
                processEscape(c2, seq.toString());
            }
        } else if (c == 19) { // Ctrl+S
            handleSaveFile();
        } else if (active == 1 || active == 2) {
            handleTyping(c);
        }
    }

    private void processEscape(int type, String seq) {
        if (type == 'O') {
            switch (seq) {
                case "A" -> handleArrowUp(false);
                case "B" -> handleArrowDown(false);
                case "C" -> handleArrowRight(false);
                case "D" -> handleArrowLeft(false);
                case "H" -> handleHome(false);
                case "F" -> handleEnd(false);
            }
        } else if (type == '[') {
            if (seq.equals("A")) handleArrowUp(false);
            else if (seq.equals("B")) handleArrowDown(false);
            else if (seq.equals("C")) handleArrowRight(false);
            else if (seq.equals("D")) handleArrowLeft(false);
            else if (seq.equals("H") || seq.equals("1~")) handleHome(false);
            else if (seq.equals("F") || seq.equals("4~")) handleEnd(false);
            else if (seq.equals("3~")) handleDelete();
            else if (seq.endsWith("A") && seq.contains(";5")) switchPanel(active >= 2 ? active - 2 : active); // Ctrl+Up
            else if (seq.endsWith("B") && seq.contains(";5")) switchPanel(active <= 1 ? active + 2 : active); // Ctrl+Down
            else if (seq.endsWith("C") && seq.contains(";5")) switchPanel(active % 2 == 0 ? active + 1 : active); // Ctrl+Right
            else if (seq.endsWith("D") && seq.contains(";5")) switchPanel(active % 2 == 1 ? active - 1 : active); // Ctrl+Left
            else if (seq.endsWith("A") && seq.contains(";2")) handleArrowUp(true); // Shift+Up
            else if (seq.endsWith("B") && seq.contains(";2")) handleArrowDown(true); // Shift+Down
            else if (seq.endsWith("C") && seq.contains(";2")) handleArrowRight(true); // Shift+Right
            else if (seq.endsWith("D") && seq.contains(";2")) handleArrowLeft(true); // Shift+Left
            else if (seq.endsWith("H") && seq.contains(";2")) handleHome(true); // Shift+Home
            else if (seq.endsWith("F") && seq.contains(";2")) handleEnd(true); // Shift+End
            else if (seq.equals("15~")) handleRunCode(); // F5
            else if (seq.equals("17~")) handleSubmitCode(); // F6
            else if (seq.equals("18~")) handleLoadFile(); // F7
        }
    }

    private void switchPanel(int p) {
        active = p;
        String name = switch (active) {
            case 0 -> "Problem (↑↓ scroll)";
            case 1 -> "Editor (Type, Shift+Arr=select)";
            case 2 -> "Test Cases (editable)";
            case 3 -> "Results (↑↓ scroll)";
            default -> "";
        };
        statusMsg = name + " │ Ctrl+Arrows: switch │ F5: run │ F6: submit │ Esc: quit";
    }

    private void checkScrollX() {
        if (active != 1) return;
        int viewW = (W / 2) - 2;
        if (curCol < scrollX) scrollX = curCol;
        else if (curCol >= scrollX + viewW) scrollX = curCol - viewW + 1;
    }

    private void checkScrollY() {
        int viewH = Math.max(3, (int) (H * 0.62)) - 2;
        if (curLine < scroll[1]) scroll[1] = curLine;
        else if (curLine >= scroll[1] + viewH) scroll[1] = curLine - viewH + 1;
    }

    private void handleArrowUp(boolean shift) {
        if (active == 1) {
            if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
            if (!shift) { anchorLine = -1; anchorCol = -1; }
            if (curLine > 0) curLine--;
            curCol = Math.min(curCol, editorLines.get(curLine).length());
            checkScrollY(); checkScrollX();
        } else {
            if (scroll[active] > 0) scroll[active]--;
        }
    }

    private void handleArrowDown(boolean shift) {
        if (active == 1) {
            if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
            if (!shift) { anchorLine = -1; anchorCol = -1; }
            if (curLine < editorLines.size() - 1) curLine++;
            curCol = Math.min(curCol, editorLines.get(curLine).length());
            checkScrollY(); checkScrollX();
        } else {
            if (scroll[active] < getActiveLines().size() - 1) scroll[active]++;
        }
    }

    private void handleArrowRight(boolean shift) {
        if (active == 1) {
            if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
            if (!shift) { anchorLine = -1; anchorCol = -1; }
            String line = editorLines.get(curLine);
            if (curCol < line.length()) curCol++;
            else if (curLine < editorLines.size() - 1) { curLine++; curCol = 0; }
            checkScrollY(); checkScrollX();
        }
    }

    private void handleArrowLeft(boolean shift) {
        if (active == 1) {
            if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
            if (!shift) { anchorLine = -1; anchorCol = -1; }
            if (curCol > 0) curCol--;
            else if (curLine > 0) { curLine--; curCol = editorLines.get(curLine).length(); }
            checkScrollY(); checkScrollX();
        }
    }

    private void handleHome(boolean shift) {
        if (active != 1) return;
        if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
        if (!shift) { anchorLine = -1; anchorCol = -1; }
        curCol = 0; checkScrollX();
    }

    private void handleEnd(boolean shift) {
        if (active != 1) return;
        if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
        if (!shift) { anchorLine = -1; anchorCol = -1; }
        curCol = editorLines.get(curLine).length(); checkScrollX();
    }

    private void deleteSelection() {
        if (anchorLine == -1) return;
        int sy = curLine, sx = curCol;
        int ey = anchorLine, ex = anchorCol;
        if (sy > ey || (sy == ey && sx > ex)) { sy = anchorLine; sx = anchorCol; ey = curLine; ex = curCol; }
        
        String before = editorLines.get(sy).substring(0, sx);
        String after = editorLines.get(ey).substring(ex);
        for (int i = ey; i > sy; i--) editorLines.remove(i);
        editorLines.set(sy, before + after);
        
        curLine = sy; curCol = sx;
        anchorLine = -1; anchorCol = -1;
        checkScrollY(); checkScrollX();
    }

    private void handleDelete() {
        if (active != 1) return;
        if (anchorLine != -1) { deleteSelection(); return; }
        String line = editorLines.get(curLine);
        if (curCol < line.length()) {
            editorLines.set(curLine, line.substring(0, curCol) + line.substring(curCol + 1));
        } else if (curLine < editorLines.size() - 1) {
            String next = editorLines.remove(curLine + 1);
            editorLines.set(curLine, line + next);
        }
    }

    private void handleTyping(int c) {
        List<String> lines = (active == 1) ? editorLines : testCaseLines;
        if (active == 1 && anchorLine != -1) deleteSelection(); // Auto-delete selected text on type
        
        int cl = (active == 1) ? curLine : Math.max(0, lines.size() - 1);

        if (c == '\r' || c == '\n') {
            if (cl < lines.size()) {
                String cur = lines.get(cl);
                int col = (active == 1) ? curCol : cur.length();
                lines.set(cl, cur.substring(0, col));
                lines.add(cl + 1, cur.substring(col));
                if (active == 1) { curLine++; curCol = 0; }
            } else {
                lines.add("");
                if (active == 1) { curLine = lines.size() - 1; curCol = 0; }
            }
        } else if (c == 127 || c == 8) { // Backspace
            if (active == 1) {
                if (curCol > 0 && cl < lines.size()) {
                    String l = lines.get(cl);
                    lines.set(cl, l.substring(0, curCol - 1) + l.substring(curCol));
                    curCol--;
                } else if (curCol == 0 && curLine > 0) {
                    String cur = lines.remove(cl);
                    curLine--;
                    curCol = lines.get(curLine).length();
                    lines.set(curLine, lines.get(curLine) + cur);
                }
            } else if (!lines.isEmpty()) {
                int li = lines.size() - 1;
                String last = lines.get(li);
                if (!last.isEmpty()) lines.set(li, last.substring(0, last.length() - 1));
                else if (lines.size() > 1) lines.remove(li);
            }
        } else if (c == '\t') {
            if (cl < lines.size()) {
                String l = lines.get(cl);
                int col = (active == 1) ? curCol : l.length();
                lines.set(cl, l.substring(0, col) + "    " + l.substring(col));
                if (active == 1) curCol += 4;
            }
        } else if (c >= 32 && c < 127) {
            if (cl < lines.size()) {
                String l = lines.get(cl);
                int col = (active == 1) ? curCol : l.length();
                lines.set(cl, l.substring(0, col) + (char) c + l.substring(col));
                if (active == 1) curCol++;
            } else {
                lines.add(String.valueOf((char) c));
                if (active == 1) { curLine = lines.size() - 1; curCol = 1; }
            }
        }
        checkScrollY(); checkScrollX();
    }

    private List<String> getActiveLines() {
        return switch (active) { case 0 -> problemLines; case 1 -> editorLines; case 2 -> testCaseLines; default -> resultLines; };
    }

    // ═══════════════════════════════════════════════════════
    //  ACTIONS (Background threads)
    // ═══════════════════════════════════════════════════════

    private String getCodeStub() {
        ProblemDetail.CodeSnippet s = problem.getCodeSnippetForLang("java");
        if (s != null) return s.getCode();
        if (problem.getCodeSnippets() != null && !problem.getCodeSnippets().isEmpty()) return problem.getCodeSnippets().get(0).getCode();
        return "// Write your solution here\n";
    }

    private void handleRunCode() {
        statusMsg = "⏳ Running code...";
        resultLines = new ArrayList<>(List.of("Running...", "Please wait..."));
        new Thread(() -> {
            try {
                String id = client.runCode(problem.getTitleSlug(), problem.getQuestionId(), "java", String.join("\n", editorLines), String.join("\n", testCaseLines));
                JsonObject r = client.waitForResult(id, 30);
                resultLines = new ArrayList<>(List.of(formatRunResult(r).split("\n")));
                statusMsg = "✓ Run complete │ Ctrl+Arrows: switch │ Esc: quit";
            } catch (Exception e) {
                resultLines = new ArrayList<>(List.of("Error:", e.getMessage()));
                statusMsg = "✗ Run failed";
            }
            render(); // Async UI update
        }).start();
    }

    private void handleSubmitCode() {
        statusMsg = "⏳ Submitting...";
        resultLines = new ArrayList<>(List.of("Submitting...", "Please wait..."));
        new Thread(() -> {
            try {
                String id = client.submitCode(problem.getTitleSlug(), problem.getQuestionId(), "java", String.join("\n", editorLines));
                JsonObject r = client.waitForResult(id, 30);
                resultLines = new ArrayList<>(List.of(formatSubmitResult(r).split("\n")));
                boolean ok = r.has("status_msg") && "Accepted".equals(r.get("status_msg").getAsString());
                statusMsg = ok ? "✓ Accepted! 🎉" : "✗ Not accepted";
            } catch (Exception e) {
                resultLines = new ArrayList<>(List.of("Error:", e.getMessage()));
                statusMsg = "✗ Submit failed";
            }
            render(); // Async UI update
        }).start();
    }

    private void handleLoadFile() {
        String slug = problem.getTitleSlug().replace("-", "_");
        Path file = Path.of(System.getProperty("user.dir"), "solutions", slug + ".java");
        if (Files.exists(file)) {
            try {
                editorLines = new ArrayList<>(List.of(Files.readString(file).replace("\t", "    ").split("\n", -1)));
                curLine = 0; curCol = 0; scroll[1] = 0; scrollX = 0; anchorLine = -1;
                statusMsg = "✓ Loaded: " + file.getFileName();
            } catch (IOException e) { statusMsg = "✗ " + e.getMessage(); }
        } else {
            resultLines = new ArrayList<>(List.of("File not found:", "  " + file, "", "Use Ctrl+S to save first."));
            statusMsg = "Not found: " + file.getFileName();
        }
    }

    private void handleSaveFile() {
        String slug = problem.getTitleSlug().replace("-", "_");
        Path dir = Path.of(System.getProperty("user.dir"), "solutions");
        Path file = dir.resolve(slug + ".java");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, String.join("\n", editorLines));
            statusMsg = "✓ Saved: " + file.getFileName();
        } catch (IOException e) { statusMsg = "✗ " + e.getMessage(); }
    }

    // ═══════════════════════════════════════════════════════
    //  RESULT FORMATTING
    // ═══════════════════════════════════════════════════════

    private String formatRunResult(JsonObject r) {
        StringBuilder sb = new StringBuilder();
        if (r.has("compile_error") && !r.get("compile_error").isJsonNull()) return "COMPILE ERROR\n\n" + r.get("compile_error").getAsString();
        if (r.has("runtime_error") && !r.get("runtime_error").isJsonNull() && !r.get("runtime_error").getAsString().isBlank()) return "RUNTIME ERROR\n\n" + r.get("runtime_error").getAsString();
        String msg = r.has("status_msg") ? r.get("status_msg").getAsString() : "Unknown";
        sb.append("Result: ").append(msg).append("\n\n");
        if (r.has("code_answer") && r.get("code_answer").isJsonArray()) {
            JsonArray ans = r.getAsJsonArray("code_answer");
            JsonArray exp = r.has("expected_code_answer") ? r.getAsJsonArray("expected_code_answer") : null;
            for (int i = 0; i < ans.size(); i++) {
                String a = ans.get(i).getAsString();
                sb.append("Test ").append(i + 1).append(": ").append(a);
                if (exp != null && i < exp.size()) sb.append(a.equals(exp.get(i).getAsString()) ? " ✓" : " ✗ exp:" + exp.get(i).getAsString());
                sb.append("\n");
            }
        }
        if (r.has("status_runtime") && !r.get("status_runtime").isJsonNull()) sb.append("\nRuntime: ").append(r.get("status_runtime").getAsString());
        return sb.toString();
    }

    private String formatSubmitResult(JsonObject r) {
        StringBuilder sb = new StringBuilder();
        String msg = r.has("status_msg") ? r.get("status_msg").getAsString() : "Unknown";
        if ("Accepted".equals(msg)) sb.append("✓ ACCEPTED!\n\n");
        else sb.append(msg).append("\n\n");
        if (r.has("status_runtime") && !r.get("status_runtime").isJsonNull()) sb.append("Runtime: ").append(r.get("status_runtime").getAsString()).append("\n");
        if (r.has("status_memory") && !r.get("status_memory").isJsonNull()) sb.append("Memory:  ").append(r.get("status_memory").getAsString()).append("\n");
        if (r.has("runtime_percentile") && !r.get("runtime_percentile").isJsonNull()) sb.append("Beats:   ").append(String.format("%.1f%%", r.get("runtime_percentile").getAsDouble())).append("\n");
        if (r.has("total_testcases") && !r.get("total_testcases").isJsonNull()) sb.append("Tests:   ").append(r.has("total_correct") ? r.get("total_correct").getAsInt() : 0).append("/").append(r.get("total_testcases").getAsInt()).append("\n");
        if (!"Accepted".equals(msg) && r.has("last_testcase") && !r.get("last_testcase").isJsonNull()) sb.append("\nFailing: ").append(r.get("last_testcase").getAsString()).append("\n");
        return sb.toString();
    }
}
