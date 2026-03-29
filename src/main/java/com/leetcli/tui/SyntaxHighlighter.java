package com.leetcli.tui;

import java.util.Map;
import java.util.Set;

/**
 * Language-aware syntax highlighter for the code editor.
 * Supports keyword highlighting, string literals, comments, and numbers.
 * Also provides language metadata (display names, file extensions).
 */
public final class SyntaxHighlighter {

    private SyntaxHighlighter() {}

    // Color indices used in the fg[] array
    public static final int DEFAULT = 0;
    public static final int KEYWORD = 1;
    public static final int STRING  = 2;
    public static final int COMMENT = 3;
    public static final int NUMBER  = 4;

    // ── Keyword sets ──

    private static final Set<String> JAVA_KW = Set.of(
        "public", "private", "protected", "class", "interface", "enum",
        "void", "int", "boolean", "double", "float", "char", "long", "byte", "short",
        "if", "else", "for", "while", "do", "switch", "case", "return", "break", "continue",
        "new", "this", "super", "try", "catch", "finally", "throw", "throws",
        "static", "final", "abstract", "import", "package", "extends", "implements",
        "String", "List", "Map", "Set", "null", "true", "false", "instanceof"
    );

    private static final Set<String> PYTHON_KW = Set.of(
        "def", "class", "if", "elif", "else", "for", "while", "return", "break", "continue",
        "import", "from", "as", "with", "try", "except", "finally", "raise", "pass",
        "lambda", "yield", "global", "nonlocal", "and", "or", "not", "in", "is",
        "True", "False", "None", "async", "await", "del", "assert"
    );

    private static final Set<String> CPP_KW = Set.of(
        "int", "float", "double", "char", "void", "bool", "long", "short", "unsigned", "signed",
        "auto", "const", "static", "class", "struct", "public", "private", "protected",
        "virtual", "override", "new", "delete", "if", "else", "for", "while", "do",
        "switch", "case", "return", "break", "continue", "using", "namespace", "include",
        "template", "typename", "nullptr", "true", "false", "throw", "try", "catch"
    );

    private static final Set<String> JS_KW = Set.of(
        "function", "var", "let", "const", "if", "else", "for", "while", "do",
        "switch", "case", "return", "break", "continue", "new", "this", "class", "extends",
        "import", "export", "from", "default", "try", "catch", "finally", "throw",
        "async", "await", "yield", "typeof", "instanceof", "of", "in",
        "true", "false", "null", "undefined"
    );

    private static final Set<String> GO_KW = Set.of(
        "func", "var", "const", "if", "else", "for", "switch", "case", "return",
        "break", "continue", "package", "import", "type", "struct", "interface",
        "map", "range", "defer", "go", "chan", "select", "fallthrough",
        "true", "false", "nil", "string", "int", "float64", "bool", "byte", "error"
    );

    public static Set<String> getKeywords(String langSlug) {
        return switch (langSlug) {
            case "java"                      -> JAVA_KW;
            case "python", "python3"         -> PYTHON_KW;
            case "cpp", "c"                  -> CPP_KW;
            case "javascript", "typescript"  -> JS_KW;
            case "golang"                    -> GO_KW;
            default                          -> JAVA_KW;
        };
    }

    public static String getCommentPrefix(String langSlug) {
        return switch (langSlug) {
            case "python", "python3", "ruby" -> "#";
            default                          -> "//";
        };
    }

    /**
     * Tokenize a line of source code into color indices.
     * Returns an int[] matching the line length.
     */
    public static int[] highlight(String line, String langSlug) {
        int[] fg = new int[line.length()];
        if (line.isEmpty()) return fg;

        String commentPrefix = getCommentPrefix(langSlug);
        boolean inStr = false, inComment = false;

        for (int i = 0; i < line.length(); i++) {
            if (inComment) { fg[i] = COMMENT; continue; }
            if (line.startsWith(commentPrefix, i) && !inStr) {
                inComment = true;
                for (int j = 0; j < commentPrefix.length() && i + j < line.length(); j++)
                    fg[i + j] = COMMENT;
                i += commentPrefix.length() - 1;
                continue;
            }
            if (line.charAt(i) == '"') {
                fg[i] = STRING;
                inStr = !inStr;
                continue;
            }
            if (inStr) { fg[i] = STRING; continue; }
            if (Character.isDigit(line.charAt(i))) fg[i] = NUMBER;
        }

        Set<String> keywords = getKeywords(langSlug);
        for (String kw : keywords) {
            int idx = -1;
            while ((idx = line.indexOf(kw, idx + 1)) != -1) {
                boolean startOk = idx == 0 || !Character.isLetterOrDigit(line.charAt(idx - 1));
                boolean endOk = idx + kw.length() == line.length()
                        || !Character.isLetterOrDigit(line.charAt(idx + kw.length()));
                if (startOk && endOk) {
                    for (int i = 0; i < kw.length(); i++)
                        if (fg[idx + i] == DEFAULT) fg[idx + i] = KEYWORD;
                }
            }
        }
        return fg;
    }

    // ── Language metadata ──

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
        Map.entry("java", "Java"),       Map.entry("python", "Python"),
        Map.entry("python3", "Python3"), Map.entry("cpp", "C++"),
        Map.entry("c", "C"),             Map.entry("csharp", "C#"),
        Map.entry("javascript", "JavaScript"), Map.entry("typescript", "TypeScript"),
        Map.entry("golang", "Go"),       Map.entry("ruby", "Ruby"),
        Map.entry("swift", "Swift"),     Map.entry("kotlin", "Kotlin"),
        Map.entry("rust", "Rust"),       Map.entry("scala", "Scala"),
        Map.entry("php", "PHP"),         Map.entry("dart", "Dart")
    );

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
        Map.entry("java", ".java"),      Map.entry("python", ".py"),
        Map.entry("python3", ".py"),     Map.entry("cpp", ".cpp"),
        Map.entry("c", ".c"),            Map.entry("csharp", ".cs"),
        Map.entry("javascript", ".js"),  Map.entry("typescript", ".ts"),
        Map.entry("golang", ".go"),      Map.entry("ruby", ".rb"),
        Map.entry("swift", ".swift"),    Map.entry("kotlin", ".kt"),
        Map.entry("rust", ".rs"),        Map.entry("scala", ".scala"),
        Map.entry("php", ".php"),        Map.entry("dart", ".dart")
    );

    public static String getDisplayName(String langSlug) {
        return DISPLAY_NAMES.getOrDefault(langSlug, langSlug);
    }

    public static String getFileExtension(String langSlug) {
        return EXTENSIONS.getOrDefault(langSlug, ".txt");
    }
}
