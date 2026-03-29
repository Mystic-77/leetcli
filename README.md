# LeetCLI

**LeetCLI** is a high-performance, terminal-based LeetCode client written in Java. It allows users to browse, read, and solve LeetCode problems entirely from within the terminal, without relying on external windowed GUIs.

## AI Model / Agent Context

If you are an AI model or agent analyzing this repository, here is the context you need to understand the project structure and technical stack:

### Project Goals
The project aims to provide a robust, terminal-based interface natively within standard terminal emulators. A major focus is the custom Text User Interface (TUI) — a notepad-like text editor within the terminal with reliable multi-byte ANSI input parsing, per-language syntax highlighting, text selection via Shift+Arrow keys, and high-contrast UI rendering on the Everforest Dark palette.

### Tech Stack & Libraries
- **Language**: Java 20
- **Build System**: Maven (see `pom.xml`, packages the app as a fat JAR via `maven-shade-plugin`).
- **CLI Framework**: [Picocli](https://picocli.info/) — command registration, subcommands, argument parsing.
- **HTTP Client**: [OkHttp 3](https://square.github.io/okhttp/) — GraphQL API requests to LeetCode.
- **JSON Parsing**: [Gson](https://github.com/google/gson).
- **HTML Parsing**: [Jsoup](https://jsoup.org/) — converts HTML problem descriptions to terminal-friendly text.
- **Terminal Handling**: [JLine 3](https://github.com/jline/jline3) — cross-platform native terminal bindings.
- **Internationalisation**: Java `ResourceBundle` — all UI strings live in `src/main/resources/messages.properties`.

### Repository Structure

```
src/main/java/com/leetcli/
├── App.java                     # Entry point. Registers Picocli subcommands.
├── commands/
│   ├── LoginCommand.java        # Authenticate with LeetCode (session cookie).
│   ├── WhoAmICommand.java       # Display logged-in user profile & stats.
│   ├── ListCommand.java         # Browse/filter LeetCode problems.
│   └── SolveCommand.java        # Launch the TUI for a specific problem.
├── tui/
│   ├── MainTUI.java             # Orchestrator: init, main loop, action dispatch.
│   ├── Theme.java               # ANSI escape constants & Everforest color palette.
│   ├── SyntaxHighlighter.java   # Per-language keyword sets, tokenizer, lang metadata.
│   ├── EditorState.java         # Editable buffer (cursor, scroll, selection). Reused
│   │                            # for both code editor (panel 1) and test case editor
│   │                            # (panel 2).
│   ├── InputHandler.java        # Raw ANSI/CSI sequence parser → KeyEvent records.
│   ├── Renderer.java            # Full-frame builder, panel layout, text wrapping,
│   │                            # syntax-highlighted editor line rendering.
│   └── Messages.java            # ResourceBundle wrapper for i18n strings.
├── api/
│   ├── LeetCodeClient.java      # HTTP client for LeetCode's GraphQL + REST APIs.
│   ├── GraphQLQueries.java      # Raw GraphQL query strings.
│   └── models/
│       ├── Problem.java         # Problem list item model.
│       └── ProblemDetail.java   # Full problem detail model (content, code stubs,
│                                # test cases, metadata).
├── config/
│   └── ConfigManager.java       # Session cookies & settings persistence (~/.leetcli).
└── util/
    └── HtmlToText.java          # HTML → plain text converter for problem descriptions.

src/main/resources/
└── messages.properties          # English UI strings (i18n ready).
```

### Architecture Notes

- **Multi-language support**: The TUI supports switching between all languages available for a problem (Java, Python, C++, JavaScript, Go, etc.) via the F8 key. `SyntaxHighlighter` provides per-language keyword sets and file extensions. `LeetCodeClient.runCode()` / `submitCode()` accept any `lang` slug the LeetCode API supports.
- **Dynamic resize handling**: The main loop detects terminal dimension changes and issues a full screen clear to prevent rendering artifacts.
- **Test case editor**: The test case panel is a full editor (same as the code editor) using `EditorState`, not an append-only text box.
- **All test cases**: `ProblemDetail.getExampleTestcaseList()` is used to load every example test case, not just the first one.

### Build & Run

```bash
# Build fat JAR
mvn clean package

# Run (any OS with Java 20+)
java -jar target/leetcli-1.0-SNAPSHOT.jar [command]

# Windows shortcut
leetcli.bat [command]

# Linux/macOS shortcut
./leetcli.sh [command]
```

### Docker (zero-install)

```bash
docker build -t leetcli .
docker run -it leetcli solve two-sum
```

### CLI Commands

| Command  | Description |
|----------|-------------|
| `login`  | Authenticate with LeetCode via session cookie |
| `whoami` | Show your profile and stats |
| `list`   | Browse and filter problems |
| `solve`  | Open the TUI to solve a problem (by ID or slug) |

### TUI Shortcuts

| Key | Action |
|-----|--------|
| F5 | Run code against test cases |
| F6 | Submit solution |
| F7 | Load solution from file |
| F8 | Switch programming language |
| Ctrl+S | Save solution to file |
| Ctrl+Arrow | Switch panel |
| Shift+Arrow | Select text in editor |
| Esc | Quit TUI |
