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

/**
 * Multi-panel TUI orchestrator.
 * Delegates rendering, input, and editing to dedicated classes.
 */
public class MainTUI {

    private final LeetCodeClient client;
    private final ProblemDetail problem;
    private final ConfigManager config;

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

    public MainTUI(LeetCodeClient client, ProblemDetail problem, ConfigManager config) {
        this.client = client;
        this.problem = problem;
        this.config = config;
    }

    public void run() throws IOException {
        terminal = TerminalBuilder.builder().system(true).jansi(true).build();
        terminal.enterRawMode();
        writer = terminal.writer();

        writer.print(Theme.ALT_BUF_ON + Theme.HIDE_CURSOR + Theme.CLEAR);
        writer.flush();

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
            writer.print(Theme.SHOW_CURSOR + Theme.RESET + Theme.ALT_BUF_OFF);
            writer.flush();
            terminal.close();
        }
    }

    // ═══════════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════════

    private void initPanels() {
        // Problem description (read-only, wrapped at render time)
        String desc = HtmlToText.convert(problem.getContent());
        problemLines = new ArrayList<>(List.of(desc.replace("\t", "    ").split("\n")));

        // Code editor
        codeEditor = new EditorState(getCodeStub());

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
        writer.flush();
    }

    // ═══════════════════════════════════════════════
    //  INPUT HANDLING
    // ═══════════════════════════════════════════════

    private void handleInput() throws IOException {
        InputHandler.KeyEvent ev = InputHandler.read(terminal.reader());

        switch (ev.action()) {
            case QUIT -> running = false;
            case SAVE -> handleSaveFile();

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
            case DELETE      -> { if (isEditorActive()) getActiveEditor().delete(); }
            case BACKSPACE   -> { if (isEditorActive()) getActiveEditor().backspace(); }
            case TAB         -> { if (isEditorActive()) getActiveEditor().insertTab(); }
            case ENTER       -> { if (isEditorActive()) getActiveEditor().enter(); }
            case CHAR        -> { if (isEditorActive()) getActiveEditor().insertChar(ev.ch()); }

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

    private void handleLoadFile() {
        String slug = problem.getTitleSlug().replace("-", "_");
        String ext = SyntaxHighlighter.getFileExtension(currentLang);
        Path file = Path.of(System.getProperty("user.dir"), "solutions", slug + ext);
        if (Files.exists(file)) {
            try {
                codeEditor.reset(Files.readString(file));
                statusMsg = Messages.get("status.loaded", file.getFileName());
            } catch (IOException e) { statusMsg = "\u2717 " + e.getMessage(); }
        } else {
            resultLines = new ArrayList<>(List.of(
                Messages.get("result.fileNotFound"), "  " + file, "",
                Messages.get("result.useSave")));
            statusMsg = Messages.get("status.notFound", file.getFileName());
        }
    }

    private void handleSaveFile() {
        String slug = problem.getTitleSlug().replace("-", "_");
        String ext = SyntaxHighlighter.getFileExtension(currentLang);
        Path dir = Path.of(System.getProperty("user.dir"), "solutions");
        Path file = dir.resolve(slug + ext);
        try {
            Files.createDirectories(dir);
            Files.writeString(file, codeEditor.getText());
            statusMsg = Messages.get("status.saved", file.getFileName());
        } catch (IOException e) { statusMsg = "\u2717 " + e.getMessage(); }
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
