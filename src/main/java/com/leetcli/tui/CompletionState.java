package com.leetcli.tui;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the auto-completion popup state: filtered items, selection, and trigger context.
 * Provides keyword, type, and snippet completions per language.
 */
public class CompletionState {

    /** A single completion item. */
    public record Item(String label, String insertText, String detail) {}

    private boolean visible = false;
    private List<Item> items = List.of();
    private int selectedIndex = 0;
    private String prefix = "";

    // ── Visibility ──

    public boolean isVisible()      { return visible; }
    public List<Item> getItems()    { return items; }
    public int getSelectedIndex()   { return selectedIndex; }
    public String getPrefix()       { return prefix; }

    /** Open the popup with completions matching the given prefix for the language. */
    public void open(String prefix, String langSlug) {
        this.prefix = prefix;
        this.items = filter(prefix, langSlug);
        this.selectedIndex = 0;
        this.visible = !items.isEmpty();
    }

    /** Close/dismiss the popup. */
    public void close() {
        visible = false;
        items = List.of();
        selectedIndex = 0;
        prefix = "";
    }

    /** Move selection up. */
    public void moveUp() {
        if (items.isEmpty()) return;
        selectedIndex = (selectedIndex - 1 + items.size()) % items.size();
    }

    /** Move selection down. */
    public void moveDown() {
        if (items.isEmpty()) return;
        selectedIndex = (selectedIndex + 1) % items.size();
    }

    /** Accept the currently selected item. Returns the item, or null if nothing selected. */
    public Item accept() {
        if (!visible || items.isEmpty()) return null;
        Item item = items.get(selectedIndex);
        close();
        return item;
    }

    /** Update the filter as the user types more characters. */
    public void updateFilter(String newPrefix, String langSlug) {
        this.prefix = newPrefix;
        this.items = filter(newPrefix, langSlug);
        this.selectedIndex = 0;
        if (items.isEmpty()) close();
    }

    // ── Filtering ──

    private List<Item> filter(String prefix, String langSlug) {
        if (prefix.isEmpty()) return List.of();

        List<Item> all = getAllCompletions(langSlug);
        String lower = prefix.toLowerCase();
        return all.stream()
            .filter(it -> it.label().toLowerCase().startsWith(lower))
            .limit(20)
            .collect(Collectors.toList());
    }

    // ── Completion sources ──

    private static List<Item> getAllCompletions(String langSlug) {
        List<Item> result = new ArrayList<>();

        // Keywords
        for (String kw : SyntaxHighlighter.getKeywords(langSlug)) {
            result.add(new Item(kw, kw, "keyword"));
        }

        // Common types
        for (String type : SyntaxHighlighter.getTypes(langSlug)) {
            result.add(new Item(type, type, "type"));
        }

        // Snippets (language-specific)
        result.addAll(getSnippets(langSlug));

        // Sort alphabetically
        result.sort(Comparator.comparing(Item::label));
        return result;
    }

    private static List<Item> getSnippets(String langSlug) {
        return switch (langSlug) {
            case "java" -> List.of(
                new Item("for",     "for (int i = 0; i < n; i++) {\n    \n}", "for loop"),
                new Item("fore",    "for (var item : collection) {\n    \n}", "for-each"),
                new Item("while",   "while (condition) {\n    \n}", "while loop"),
                new Item("if",      "if (condition) {\n    \n}", "if block"),
                new Item("ifelse",  "if (condition) {\n    \n} else {\n    \n}", "if-else"),
                new Item("sout",    "System.out.println();", "print"),
                new Item("bfs",     "Queue<Integer> queue = new LinkedList<>();\nqueue.offer(start);\nSet<Integer> visited = new HashSet<>();\nvisited.add(start);\nwhile (!queue.isEmpty()) {\n    int curr = queue.poll();\n    // process curr\n    for (int next : adj.get(curr)) {\n        if (!visited.contains(next)) {\n            visited.add(next);\n            queue.offer(next);\n        }\n    }\n}", "BFS template"),
                new Item("dfs",     "private void dfs(int node, Set<Integer> visited) {\n    visited.add(node);\n    // process node\n    for (int next : adj.get(node)) {\n        if (!visited.contains(next)) {\n            dfs(next, visited);\n        }\n    }\n}", "DFS template"),
                new Item("bsearch", "int lo = 0, hi = n - 1;\nwhile (lo <= hi) {\n    int mid = lo + (hi - lo) / 2;\n    if (arr[mid] == target) return mid;\n    else if (arr[mid] < target) lo = mid + 1;\n    else hi = mid - 1;\n}\nreturn -1;", "binary search"),
                new Item("swap",    "int temp = a; a = b; b = temp;", "swap values"),
                new Item("map",     "Map<String, Integer> map = new HashMap<>();", "HashMap init"),
                new Item("list",    "List<Integer> list = new ArrayList<>();", "ArrayList init"),
                new Item("pq",      "PriorityQueue<Integer> pq = new PriorityQueue<>();", "PriorityQueue init"),
                new Item("sort",    "Arrays.sort(arr);", "sort array")
            );
            case "python", "python3" -> List.of(
                new Item("for",     "for i in range(n):\n    ", "for loop"),
                new Item("fore",    "for item in collection:\n    ", "for-each"),
                new Item("while",   "while condition:\n    ", "while loop"),
                new Item("if",      "if condition:\n    ", "if block"),
                new Item("ifelse",  "if condition:\n    \nelse:\n    ", "if-else"),
                new Item("bfs",     "queue = deque([start])\nvisited = {start}\nwhile queue:\n    curr = queue.popleft()\n    # process curr\n    for next_node in adj[curr]:\n        if next_node not in visited:\n            visited.add(next_node)\n            queue.append(next_node)", "BFS template"),
                new Item("dfs",     "def dfs(node, visited):\n    visited.add(node)\n    # process node\n    for next_node in adj[node]:\n        if next_node not in visited:\n            dfs(next_node, visited)", "DFS template"),
                new Item("bsearch", "lo, hi = 0, n - 1\nwhile lo <= hi:\n    mid = (lo + hi) // 2\n    if arr[mid] == target:\n        return mid\n    elif arr[mid] < target:\n        lo = mid + 1\n    else:\n        hi = mid - 1\nreturn -1", "binary search"),
                new Item("dd",      "defaultdict(int)", "defaultdict"),
                new Item("counter", "Counter(arr)", "Counter"),
                new Item("heap",    "heapq.heappush(heap, val)", "heappush"),
                new Item("sort",    "arr.sort()", "sort list")
            );
            case "cpp", "c" -> List.of(
                new Item("for",     "for (int i = 0; i < n; i++) {\n    \n}", "for loop"),
                new Item("fore",    "for (auto& item : collection) {\n    \n}", "range-for"),
                new Item("while",   "while (condition) {\n    \n}", "while loop"),
                new Item("if",      "if (condition) {\n    \n}", "if block"),
                new Item("bfs",     "queue<int> q;\nq.push(start);\nunordered_set<int> visited;\nvisited.insert(start);\nwhile (!q.empty()) {\n    int curr = q.front(); q.pop();\n    // process curr\n    for (int next : adj[curr]) {\n        if (!visited.count(next)) {\n            visited.insert(next);\n            q.push(next);\n        }\n    }\n}", "BFS template"),
                new Item("dfs",     "void dfs(int node, unordered_set<int>& visited) {\n    visited.insert(node);\n    // process node\n    for (int next : adj[node]) {\n        if (!visited.count(next)) {\n            dfs(next, visited);\n        }\n    }\n}", "DFS template"),
                new Item("bsearch", "int lo = 0, hi = n - 1;\nwhile (lo <= hi) {\n    int mid = lo + (hi - lo) / 2;\n    if (arr[mid] == target) return mid;\n    else if (arr[mid] < target) lo = mid + 1;\n    else hi = mid - 1;\n}\nreturn -1;", "binary search"),
                new Item("vec",     "vector<int> v;", "vector init"),
                new Item("umap",    "unordered_map<int, int> mp;", "unordered_map init"),
                new Item("sort",    "sort(v.begin(), v.end());", "sort vector")
            );
            case "javascript", "typescript" -> List.of(
                new Item("for",     "for (let i = 0; i < n; i++) {\n    \n}", "for loop"),
                new Item("fore",    "for (const item of collection) {\n    \n}", "for-of"),
                new Item("while",   "while (condition) {\n    \n}", "while loop"),
                new Item("if",      "if (condition) {\n    \n}", "if block"),
                new Item("fn",      "function name(params) {\n    \n}", "function"),
                new Item("arrow",   "const fn = (params) => {\n    \n};", "arrow function"),
                new Item("map",     "new Map()", "Map init"),
                new Item("set",     "new Set()", "Set init"),
                new Item("sort",    "arr.sort((a, b) => a - b);", "sort array")
            );
            case "golang" -> List.of(
                new Item("for",     "for i := 0; i < n; i++ {\n    \n}", "for loop"),
                new Item("forr",    "for _, v := range collection {\n    \n}", "range for"),
                new Item("if",      "if condition {\n    \n}", "if block"),
                new Item("iferr",   "if err != nil {\n    return err\n}", "error check"),
                new Item("sort",    "sort.Slice(arr, func(i, j int) bool {\n    return arr[i] < arr[j]\n})", "sort slice")
            );
            default -> List.of();
        };
    }
}
