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
    public static final int DEFAULT    = 0;
    public static final int KEYWORD    = 1;
    public static final int STRING     = 2;
    public static final int COMMENT    = 3;
    public static final int NUMBER     = 4;
    public static final int TYPE       = 5;
    public static final int ANNOTATION = 6;
    public static final int OPERATOR   = 7;
    public static final int BRACKET    = 8;

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

    // ── Common type sets (for TYPE highlighting) ──

    private static final Set<String> JAVA_TYPES = Set.of(
        "HashMap", "ArrayList", "LinkedList", "TreeMap", "TreeSet",
        "HashSet", "PriorityQueue", "Stack", "Queue", "Deque", "ArrayDeque",
        "Arrays", "Collections", "Math", "StringBuilder", "StringBuffer",
        "ListNode", "TreeNode", "Optional", "Stream", "Comparator",
        "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte",
        "Object", "Number", "Comparable", "Iterable", "Iterator",
        "Exception", "RuntimeException", "IllegalArgumentException"
    );

    private static final Set<String> PYTHON_TYPES = Set.of(
        "list", "dict", "set", "tuple", "str", "int", "float", "bool",
        "defaultdict", "Counter", "deque", "OrderedDict", "namedtuple",
        "ListNode", "TreeNode", "Optional", "List", "Dict", "Set", "Tuple"
    );

    private static final Set<String> CPP_TYPES = Set.of(
        "vector", "map", "unordered_map", "set", "unordered_set",
        "priority_queue", "stack", "queue", "deque", "pair",
        "string", "array", "bitset", "tuple", "optional",
        "ListNode", "TreeNode", "size_t", "int64_t", "uint32_t"
    );

    private static final Set<String> JS_TYPES = Set.of(
        "Array", "Map", "Set", "WeakMap", "WeakSet", "Promise",
        "Number", "String", "Boolean", "Object", "Symbol", "BigInt",
        "RegExp", "Date", "Error", "JSON", "Math", "console",
        "ListNode", "TreeNode"
    );

    private static final Set<String> GO_TYPES = Set.of(
        "ListNode", "TreeNode", "sort", "fmt", "math", "strings", "strconv"
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

    public static Set<String> getTypes(String langSlug) {
        return switch (langSlug) {
            case "java"                      -> JAVA_TYPES;
            case "python", "python3"         -> PYTHON_TYPES;
            case "cpp", "c"                  -> CPP_TYPES;
            case "javascript", "typescript"  -> JS_TYPES;
            case "golang"                    -> GO_TYPES;
            default                          -> JAVA_TYPES;
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
    private static final String BRACKETS = "(){}[]";
    private static final String[] OPERATORS = {
        "->", "==", "!=", "<=", ">=", "&&", "||",
        "+=", "-=", "*=", "/=", "%=", "++", "--",
        "<<", ">>", "&=", "|=", "^="
    };

    public static int[] highlight(String line, String langSlug) {
        int[] fg = new int[line.length()];
        if (line.isEmpty()) return fg;

        String commentPrefix = getCommentPrefix(langSlug);
        boolean inStr = false, inComment = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (inComment) { fg[i] = COMMENT; continue; }
            if (line.startsWith(commentPrefix, i) && !inStr) {
                inComment = true;
                for (int j = 0; j < commentPrefix.length() && i + j < line.length(); j++)
                    fg[i + j] = COMMENT;
                i += commentPrefix.length() - 1;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                fg[i] = STRING;
                if (ch == '"') inStr = !inStr;
                continue;
            }
            if (inStr) { fg[i] = STRING; continue; }

            // Annotations: @Override, @param, etc.
            if (ch == '@' && i + 1 < line.length() && Character.isLetter(line.charAt(i + 1))) {
                fg[i] = ANNOTATION;
                int j = i + 1;
                while (j < line.length() && Character.isLetterOrDigit(line.charAt(j))) {
                    fg[j] = ANNOTATION;
                    j++;
                }
                i = j - 1;
                continue;
            }

            // Brackets
            if (BRACKETS.indexOf(ch) >= 0) { fg[i] = BRACKET; continue; }

            // Numbers
            if (Character.isDigit(ch)) { fg[i] = NUMBER; continue; }

            // Multi-char operators
            boolean matchedOp = false;
            for (String op : OPERATORS) {
                if (line.startsWith(op, i)) {
                    for (int j = 0; j < op.length(); j++) fg[i + j] = OPERATOR;
                    i += op.length() - 1;
                    matchedOp = true;
                    break;
                }
            }
            if (matchedOp) continue;

            // Single-char operators
            if ("=+*/%<>!&|^~?".indexOf(ch) >= 0) { fg[i] = OPERATOR; }
        }

        // Word-level pass: keywords and types
        Set<String> keywords = getKeywords(langSlug);
        Set<String> types = getTypes(langSlug);
        highlightWords(line, fg, keywords, KEYWORD);
        highlightWords(line, fg, types, TYPE);
        return fg;
    }

    /** Highlight whole-word matches of wordSet with the given color, only if currently DEFAULT. */
    private static void highlightWords(String line, int[] fg, Set<String> wordSet, int color) {
        for (String word : wordSet) {
            int idx = -1;
            while ((idx = line.indexOf(word, idx + 1)) != -1) {
                boolean startOk = idx == 0 || !Character.isLetterOrDigit(line.charAt(idx - 1));
                boolean endOk = idx + word.length() == line.length()
                        || !Character.isLetterOrDigit(line.charAt(idx + word.length()));
                if (startOk && endOk) {
                    for (int i = 0; i < word.length(); i++)
                        if (fg[idx + i] == DEFAULT) fg[idx + i] = color;
                }
            }
        }
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
