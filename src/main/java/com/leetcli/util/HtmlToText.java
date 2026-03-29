package com.leetcli.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

/**
 * Converts HTML problem descriptions to readable plain text for the terminal.
 */
public class HtmlToText {

    /**
     * Convert HTML to plain text suitable for terminal display.
     */
    public static String convert(String html) {
        if (html == null || html.isBlank()) return "";

        Document doc = Jsoup.parse(html);

        StringBuilder sb = new StringBuilder();
        doc.body().traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode textNode) {
                    String text = textNode.getWholeText();
                    // Collapse whitespace but preserve meaningful spacing
                    if (!text.isBlank()) {
                        sb.append(text.replaceAll("\\s+", " "));
                    }
                } else if (node instanceof Element el) {
                    switch (el.tagName()) {
                        case "br" -> sb.append("\n");
                        case "p", "div" -> {
                            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                                sb.append("\n\n");
                            }
                        }
                        case "li" -> sb.append("\n  • ");
                        case "ul", "ol" -> sb.append("\n");
                        case "h1", "h2", "h3", "h4" -> {
                            sb.append("\n");
                        }
                        case "pre" -> sb.append("\n");
                        case "code" -> {
                            // Only add backtick-feel for inline code, not pre>code blocks
                            if (el.parent() == null || !el.parent().tagName().equals("pre")) {
                                sb.append("`");
                            }
                        }
                        case "strong", "b" -> sb.append("");
                        case "em", "i" -> sb.append("");
                        case "sup" -> sb.append("^");
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element el) {
                    switch (el.tagName()) {
                        case "p", "div" -> {
                            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                                sb.append("\n");
                            }
                        }
                        case "pre" -> sb.append("\n");
                        case "code" -> {
                            if (el.parent() == null || !el.parent().tagName().equals("pre")) {
                                sb.append("`");
                            }
                        }
                        case "h1", "h2", "h3", "h4" -> sb.append("\n");
                        case "ul", "ol" -> sb.append("\n");
                    }
                }
            }
        });

        // Clean up excessive blank lines
        return sb.toString()
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .trim();
    }
}
