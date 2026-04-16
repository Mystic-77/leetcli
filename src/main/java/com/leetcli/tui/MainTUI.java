package com.leetcli.tui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.leetcli.api.LeetCodeClient;
import com.leetcli.api.models.ProblemDetail;
import com.leetcli.config.ConfigManager;
import com.leetcli.util.HtmlToText;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Multi-panel TUI orchestrator.
 * Delegates rendering, input, and editing to dedicated classes.
 */
public class MainTUI {

    private final LeetCodeClient client;
    private final ProblemDetail problem;

    private Terminal terminal;
    private PrintWriter writer;
    private int W, H, prevW, prevH;

    // Panel content
    private List<String> problemLines;
    private EditorState codeEditor;
    private EditorState testEditor;
    private List<String> resultLines;

    // Scroll offsets for read-only panels
    private int scrollProblem = 0;
    private int scrollResults = 0;

    // Active panel: 0=problem, 1=editor, 2=tests, 3=results
    private int active = 1;

    // Multi-language support
    private String currentLang = "java";
    private List<String> availableLangs;

    private String statusMsg = "";
    private volatile boolean running = true;

    // Completion popup
    private final CompletionState completion = new CompletionState();

    // Auto-save: debounced timer fires 2s after last edit
    private Timer autoSaveTimer;
    private static final long AUTO_SAVE_DELAY_MS = 2000;

    private boolean externalTerminal = false;

    public MainTUI(LeetCodeClient client, ProblemDetail problem, ConfigManager config) {
        this.client = client;
        this.problem = problem;
    }

    public MainTUI(LeetCodeClient client, ProblemDetail problem, ConfigManager config, Terminal terminal, PrintWriter writer) {
        this.client = client;
        this.problem = problem;
        this.terminal = terminal;
        this.writer = writer;
        this.externalTerminal = (terminal != null);
    }

    public void run() throws IOException {
        if (!externalTerminal) {
            terminal = TerminalBuilder.builder().system(true).jansi(true).build();
            terminal.enterRawMode();
            writer = terminal.writer();

            writer.print(Theme.ALT_BUF_ON + Theme.HIDE_CURSOR + Theme.CLEAR);
            writer.flush();
        } else {
            // Clear once when entering solver from browser
            writer.print(Theme.CLEAR);
            writer.flush();
        }

        try {
            initPanels();

            while (running) {
                W = terminal.getWidth();
                H = terminal.getHeight();

                // ── Resize detection: clear and repaint ──
                if (W != prevW || H != prevH) {
                    writer.print(Theme.CLEAR);
                    writer.flush();
                    prevW = W;
                    prevH = H;
                }

                if (W < 40 || H < 10) {
                    writer.print(Theme.ESC + "1;1H" + Theme.CLEAR);
                    writer.print(Messages.get("status.termTooSmall"));
                    writer.flush();
                    handleInput();
                    continue;
                }

                render();
                handleInput();
            }
        } finally {
            // Final save before exit
            silentSave();
            if (autoSaveTimer != null) autoSaveTimer.cancel();
            
            if (!externalTerminal) {
                writer.print(Theme.SHOW_CURSOR + Theme.RESET + Theme.ALT_BUF_OFF);
                writer.flush();
                terminal.close();
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════════

    private void initPanels() {
        // Problem description (read-only, wrapped at render time)
        String desc = HtmlToText.convert(problem.getContent());
        problemLines = new ArrayList<>(List.of(desc.replace("\t", "    ").split("\n")));

        // Code editor — auto-load from saved file if available
        String savedCode = tryLoadSavedFile();
        codeEditor = new EditorState(savedCode != null ? savedCode : getCodeStub());

        // Test cases — load ALL examples, not just the first
        String allTests = buildAllTestCases();
        testEditor = new EditorState(allTests);

        // Available languages from the problem's code snippets
        availableLangs = new ArrayList<>();
        if (problem.getCodeSnippets() != null) {
            for (var s : problem.getCodeSnippets()) {
                availableLangs.add(s.getLangSlug());
            }
        }
        if (availableLangs.isEmpty()) availableLangs.add("java");
        if (!availableLangs.contains(currentLang)) currentLang = availableLangs.get(0);

        // Results / shortcuts
        resultLines = new ArrayList<>(List.of(
            Messages.get("shortcuts.title"), "",
            Messages.get("shortcuts.run"),
            Messages.get("shortcuts.submit"),
            Messages.get("shortcuts.load"),
            Messages.get("shortcuts.save"),
            Messages.get("shortcuts.switch"),
            Messages.get("shortcuts.select"),
            Messages.get("shortcuts.lang"),
            Messages.get("shortcuts.quit"), "",
            Messages.get("shortcuts.tab")
        ));

        switchPanel(1);
    }

    /** Join all example test cases with a blank separator line. */
    private String buildAllTestCases() {
        List<String> examples = problem.getExampleTestcaseList();
        if (examples != null && !examples.isEmpty()) {
            return String.join("\n", examples);
        }
        String single = problem.getSampleTestCase();
        return single != null ? single : "";
    }

    // ═══════════════════════════════════════════════
    //  RENDERING
    // ═══════════════════════════════════════════════

    private void render() {
        String frame = Renderer.renderFrame(
            W, H, problem, problemLines,
            codeEditor, testEditor, resultLines,
            scrollProblem, scrollResults,
            active, currentLang, statusMsg
        );
        writer.print(frame);

        // Completion popup overlay (only in code editor panel)
        if (completion.isVisible() && active == 1) {
            int[] pos = cursorTerminalPos();
            String popupStr = Renderer.renderPopup(completion, pos[0], pos[1], H);
            writer.print(popupStr);
        }

        writer.flush();
    }

    /** Compute the terminal row/col of the code editor's cursor. */
    private int[] cursorTerminalPos() {
        int leftW = W / 2;
        // Editor panel starts at column leftW + 2 (border + 1)
        int col = leftW + 2 + (codeEditor.getCurCol() - codeEditor.getScrollX());
        // Row 1 = title, Row 2 = top border, content starts at row 3
        int row = 3 + (codeEditor.getCurLine() - codeEditor.getScrollY());
        return new int[]{row, col};
    }

    // ═══════════════════════════════════════════════
    //  INPUT HANDLING
    // ═══════════════════════════════════════════════

    private void handleInput() throws IOException {
        InputHandler.KeyEvent ev = InputHandler.read(terminal.reader());

        // ── Completion popup intercepts ──
        if (completion.isVisible()) {
            switch (ev.action()) {
                case ARROW_UP   -> { completion.moveUp(); return; }
                case ARROW_DOWN -> { completion.moveDown(); return; }
                case TAB, ENTER -> { acceptCompletion(); return; }
                case ESCAPE     -> { completion.close(); return; }
                case CHAR       -> {
                    // Type-ahead: insert char and update filter
                    getActiveEditor().insertChar(ev.ch());
                    scheduleAutoSave();
                    String prefix = wordBeforeCursor(getActiveEditor());
                    completion.updateFilter(prefix, currentLang);
                    return;
                }
                case BACKSPACE  -> {
                    getActiveEditor().backspace();
                    scheduleAutoSave();
                    String prefix = wordBeforeCursor(getActiveEditor());
                    if (prefix.isEmpty()) completion.close();
                    else completion.updateFilter(prefix, currentLang);
                    return;
                }
                default -> { completion.close(); } // any other key dismisses
            }
        }

        switch (ev.action()) {
            case ESCAPE -> running = false; // ESC without popup = quit
            case QUIT   -> running = false;
            case SAVE   -> handleSaveFile();

            // ── Completion trigger ──
            case CTRL_SPACE -> {
                if (isEditorActive()) {
                    String prefix = wordBeforeCursor(getActiveEditor());
                    completion.open(prefix, currentLang);
                }
            }

            // ── Panel switching (Ctrl+Arrow) ──
            case CTRL_UP    -> switchPanel(active >= 2 ? active - 2 : active);
            case CTRL_DOWN  -> switchPanel(active <= 1 ? active + 2 : active);
            case CTRL_RIGHT -> switchPanel(active % 2 == 0 ? active + 1 : active);
            case CTRL_LEFT  -> switchPanel(active % 2 == 1 ? active - 1 : active);

            // ── Editor actions (panels 1 & 2) ──
            case ARROW_UP    -> handleEditorOrScroll(() -> getActiveEditor().moveUp(false), -1);
            case ARROW_DOWN  -> handleEditorOrScroll(() -> getActiveEditor().moveDown(false), 1);
            case ARROW_LEFT  -> { if (isEditorActive()) getActiveEditor().moveLeft(false); }
            case ARROW_RIGHT -> { if (isEditorActive()) getActiveEditor().moveRight(false); }
            case SHIFT_UP    -> { if (isEditorActive()) getActiveEditor().moveUp(true); }
            case SHIFT_DOWN  -> { if (isEditorActive()) getActiveEditor().moveDown(true); }
            case SHIFT_LEFT  -> { if (isEditorActive()) getActiveEditor().moveLeft(true); }
            case SHIFT_RIGHT -> { if (isEditorActive()) getActiveEditor().moveRight(true); }
            case HOME        -> { if (isEditorActive()) getActiveEditor().home(false); }
            case END         -> { if (isEditorActive()) getActiveEditor().end(false); }
            case SHIFT_HOME  -> { if (isEditorActive()) getActiveEditor().home(true); }
            case SHIFT_END   -> { if (isEditorActive()) getActiveEditor().end(true); }
            case DELETE      -> { if (isEditorActive()) { getActiveEditor().delete(); scheduleAutoSave(); } }
            case BACKSPACE   -> { if (isEditorActive()) { getActiveEditor().backspace(); scheduleAutoSave(); } }
            case TAB         -> { if (isEditorActive()) { getActiveEditor().insertTab(); scheduleAutoSave(); } }
            case ENTER       -> { if (isEditorActive()) { getActiveEditor().enter(); scheduleAutoSave(); } }
            case CHAR        -> { if (isEditorActive()) { getActiveEditor().insertChar(ev.ch()); scheduleAutoSave(); } }

            // ── Actions ──
            case RUN         -> handleRunCode();
            case SUBMIT      -> handleSubmitCode();
            case LOAD_FILE   -> handleLoadFile();
            case SWITCH_LANG -> handleSwitchLang();

            default -> {}
        }

        // Keep cursor in view for editable panels
        if (isEditorActive()) {
            int viewW = (active == 1 ? W - W / 2 : W / 2) - 2;
            int viewH = active == 1 ? Renderer.topViewH(H) : Renderer.botViewH(H);
            getActiveEditor().ensureVisible(viewW, viewH);
        }
    }

    /** Extract the word (identifier) immediately before the cursor. */
    private String wordBeforeCursor(EditorState editor) {
        String line = editor.getLine(editor.getCurLine());
        int col = editor.getCurCol();
        int start = col;
        while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) start--;
        return line.substring(start, col);
    }

    /** Accept the selected completion item and insert it into the editor. */
    private void acceptCompletion() {
        CompletionState.Item item = completion.accept();
        if (item == null) return;

        EditorState editor = getActiveEditor();
        String line = editor.getLine(editor.getCurLine());
        int col = editor.getCurCol();

        // Find the prefix start
        int start = col;
        while (start > 0 && Character.isLetterOrDigit(line.charAt(start - 1))) start--;

        // Replace prefix with the insert text
        String insertText = item.insertText();
        String before = line.substring(0, start);
        String after = line.substring(col);

        // Handle multi-line insertions (snippets)
        String[] insertLines = insertText.split("\n", -1);
        if (insertLines.length == 1) {
            // Single line — simple replacement
            editor.getLines().set(editor.getCurLine(), before + insertText + after);
            // Position cursor at end of inserted text
            int newCol = start + insertText.length();
            // Use moveRight/moveLeft to set cursor position
            while (editor.getCurCol() > newCol) editor.moveLeft(false);
            while (editor.getCurCol() < newCol) editor.moveRight(false);
        } else {
            // Multi-line snippet: compute indent from current line
            String indent = "";
            for (int i = 0; i < before.length(); i++) {
                if (before.charAt(i) == ' ') indent += " ";
                else break;
            }

            // First line
            editor.getLines().set(editor.getCurLine(), before + insertLines[0]);

            // Middle + last lines
            for (int i = 1; i < insertLines.length; i++) {
                editor.getLines().add(editor.getCurLine() + i, indent + insertLines[i]);
            }

            // Append the remaining text after the snippet to the last line
            int lastIdx = editor.getCurLine() + insertLines.length - 1;
            editor.getLines().set(lastIdx, editor.getLines().get(lastIdx) + after);
        }
        scheduleAutoSave();
    }

    private boolean isEditorActive() { return active == 1 || active == 2; }

    private EditorState getActiveEditor() {
        return active == 2 ? testEditor : codeEditor;
    }

    /** For arrow up/down: move cursor in editors, scroll in read-only panels. */
    private void handleEditorOrScroll(Runnable editorAction, int scrollDelta) {
        if (isEditorActive()) {
            editorAction.run();
        } else if (active == 0) {
            scrollProblem = Math.max(0, scrollProblem + scrollDelta);
        } else {
            scrollResults = Math.max(0, scrollResults + scrollDelta);
        }
    }

    private void switchPanel(int p) {
        active = p;
        String name = switch (active) {
            case 0 -> Messages.get("status.problem");
            case 1 -> Messages.get("status.editor");
            case 2 -> Messages.get("status.testcases");
            case 3 -> Messages.get("status.results");
            default -> "";
        };
        statusMsg = name + " " + Messages.get("status.suffix");
    }

    // ═══════════════════════════════════════════════
    //  LANGUAGE SWITCHING
    // ═══════════════════════════════════════════════

    private void handleSwitchLang() {
        if (availableLangs.size() <= 1) return;
        int idx = availableLangs.indexOf(currentLang);
        currentLang = availableLangs.get((idx + 1) % availableLangs.size());

        // Reload code stub for the new language
        codeEditor.reset(getCodeStub());
        statusMsg = Messages.get("status.langSwitched", SyntaxHighlighter.getDisplayName(currentLang));
    }

    private String getCodeStub() {
        ProblemDetail.CodeSnippet s = problem.getCodeSnippetForLang(currentLang);
        if (s != null) return s.getCode();
        if (problem.getCodeSnippets() != null && !problem.getCodeSnippets().isEmpty())
            return problem.getCodeSnippets().get(0).getCode();
        return "// Write your solution here\n";
    }

    // ═══════════════════════════════════════════════
    //  ACTIONS
    // ═══════════════════════════════════════════════

    private void handleRunCode() {
        statusMsg = Messages.get("status.running");
        resultLines = new ArrayList<>(List.of(Messages.get("result.running"), Messages.get("result.pleaseWait")));
        new Thread(() -> {
            try {
                String id = client.runCode(problem.getTitleSlug(), problem.getQuestionId(),
                        currentLang, codeEditor.getText(), testEditor.getText());
                JsonObject r = client.waitForResult(id, 30);
                resultLines = new ArrayList<>(List.of(formatRunResult(r).split("\n")));
                statusMsg = Messages.get("status.runComplete");
            } catch (Exception e) {
                resultLines = new ArrayList<>(List.of(Messages.get("result.error"), e.getMessage()));
                statusMsg = Messages.get("status.runFailed");
            }
            render();
        }).start();
    }

    private void handleSubmitCode() {
        statusMsg = Messages.get("status.submitting");
        resultLines = new ArrayList<>(List.of(Messages.get("result.submitting"), Messages.get("result.pleaseWait")));
        new Thread(() -> {
            try {
                String id = client.submitCode(problem.getTitleSlug(), problem.getQuestionId(),
                        currentLang, codeEditor.getText());
                JsonObject r = client.waitForResult(id, 30);
                resultLines = new ArrayList<>(List.of(formatSubmitResult(r).split("\n")));
                boolean ok = r.has("status_msg") && "Accepted".equals(r.get("status_msg").getAsString());
                statusMsg = ok ? Messages.get("status.accepted") : Messages.get("status.notAccepted");
            } catch (Exception e) {
                resultLines = new ArrayList<>(List.of(Messages.get("result.error"), e.getMessage()));
                statusMsg = Messages.get("status.submitFailed");
            }
            render();
        }).start();
    }

    private Path solutionsDir() {
        return Path.of(System.getProperty("user.dir"), "solutions");
    }

    private Path getSafeSolutionFile() {
        String slug = problem.getTitleSlug().replace("-", "_");
        if (!slug.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid slug: " + slug);
        }
        String ext = SyntaxHighlighter.getFileExtension(currentLang);
        Path dir = solutionsDir();
        Path file = dir.resolve(slug + ext).normalize();
        
        if (!file.startsWith(dir.normalize())) {
            throw new IllegalArgumentException("Path traversal detected");
        }
        return file;
    }

    private void handleLoadFile() {
        try {
            Path file = getSafeSolutionFile();
            if (Files.exists(file)) {
                codeEditor.reset(Files.readString(file));
                statusMsg = Messages.get("status.loaded", file.getFileName());
            } else {
                resultLines = new ArrayList<>(List.of(
                    Messages.get("result.fileNotFound"), "  " + file, "",
                    Messages.get("result.useSave")));
                statusMsg = Messages.get("status.notFound", file.getFileName());
            }
        } catch (Exception e) { statusMsg = "\u2717 " + e.getMessage(); }
    }

    private void handleSaveFile() {
        try {
            Path file = getSafeSolutionFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, codeEditor.getText());
            statusMsg = Messages.get("status.saved", file.getFileName());
        } catch (Exception e) { statusMsg = "\u2717 " + e.getMessage(); }
    }

    /** Try to load a previously saved file for the current problem/lang. */
    private String tryLoadSavedFile() {
        try {
            Path file = getSafeSolutionFile();
            if (Files.exists(file)) {
                statusMsg = Messages.get("status.loaded", file.getFileName());
                return Files.readString(file);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Silent save — no status message update (used for auto-save and exit save). */
    private void silentSave() {
        try {
            Path file = getSafeSolutionFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, codeEditor.getText());
        } catch (Exception ignored) {}
    }

    /** Schedule an auto-save 2 seconds after the last edit (debounced). */
    private void scheduleAutoSave() {
        if (autoSaveTimer != null) autoSaveTimer.cancel();
        autoSaveTimer = new Timer(true); // daemon thread
        autoSaveTimer.schedule(new TimerTask() {
            @Override public void run() { silentSave(); }
        }, AUTO_SAVE_DELAY_MS);
    }

    // ═══════════════════════════════════════════════
    //  RESULT FORMATTING
    // ═══════════════════════════════════════════════

    private String formatRunResult(JsonObject r) {
        StringBuilder sb = new StringBuilder();
        if (r.has("compile_error") && !r.get("compile_error").isJsonNull())
            return Messages.get("result.compileError") + "\n\n" + r.get("compile_error").getAsString();
        if (r.has("runtime_error") && !r.get("runtime_error").isJsonNull()
                && !r.get("runtime_error").getAsString().isBlank())
            return Messages.get("result.runtimeError") + "\n\n" + r.get("runtime_error").getAsString();

        String msg = r.has("status_msg") ? r.get("status_msg").getAsString() : "Unknown";
        sb.append(Messages.get("result.result", msg)).append("\n\n");

        if (r.has("code_answer") && r.get("code_answer").isJsonArray()) {
            JsonArray ans = r.getAsJsonArray("code_answer");
            JsonArray exp = r.has("expected_code_answer") ? r.getAsJsonArray("expected_code_answer") : null;
            for (int i = 0; i < ans.size(); i++) {
                String a = ans.get(i).getAsString();
                sb.append(Messages.get("result.test", i + 1, a));
                if (exp != null && i < exp.size())
                    sb.append(a.equals(exp.get(i).getAsString()) ? " \u2713" : " \u2717 exp:" + exp.get(i).getAsString());
                sb.append("\n");
            }
        }

        if (r.has("status_runtime") && !r.get("status_runtime").isJsonNull())
            sb.append("\n").append(Messages.get("result.runtime", r.get("status_runtime").getAsString()));
        return sb.toString();
    }

    private String formatSubmitResult(JsonObject r) {
        StringBuilder sb = new StringBuilder();
        String msg = r.has("status_msg") ? r.get("status_msg").getAsString() : "Unknown";

        if ("Accepted".equals(msg)) sb.append(Messages.get("result.acceptedBanner")).append("\n\n");
        else sb.append(msg).append("\n\n");

        if (r.has("status_runtime") && !r.get("status_runtime").isJsonNull())
            sb.append(Messages.get("result.runtime", r.get("status_runtime").getAsString())).append("\n");
        if (r.has("status_memory") && !r.get("status_memory").isJsonNull())
            sb.append(Messages.get("result.memory", r.get("status_memory").getAsString())).append("\n");
        if (r.has("runtime_percentile") && !r.get("runtime_percentile").isJsonNull())
            sb.append(Messages.get("result.beats", String.format("%.1f%%", r.get("runtime_percentile").getAsDouble()))).append("\n");
        if (r.has("total_testcases") && !r.get("total_testcases").isJsonNull())
            sb.append(Messages.get("result.tests",
                    r.has("total_correct") ? r.get("total_correct").getAsInt() : 0,
                    r.get("total_testcases").getAsInt())).append("\n");
        if (!"Accepted".equals(msg) && r.has("last_testcase") && !r.get("last_testcase").isJsonNull())
            sb.append("\n").append(Messages.get("result.failing", r.get("last_testcase").getAsString())).append("\n");
        return sb.toString();
    }
}
