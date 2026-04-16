package com.leetcli.tui;

/**
 * Centralised ANSI color theme and terminal control sequences.
 * Based on the Everforest Dark (Hard) palette.
 */
public final class Theme {

    private Theme() {}

    // ── Terminal control ──
    public static final String ESC = "\u001b[";

    // Everforest Dark (Hard) base
    public static final String BG_RGB = "48;2;45;53;59m";
    public static final String FG_RGB = "38;2;211;198;170m";

    public static final String RESET        = ESC + "0m" + ESC + BG_RGB + ESC + FG_RGB;
    public static final String HIDE_CURSOR  = ESC + "?25l";
    public static final String SHOW_CURSOR  = ESC + "?25h";
    public static final String ALT_BUF_ON   = ESC + "?1049h";
    public static final String ALT_BUF_OFF  = ESC + "?1049l";
    public static final String AUTOWRAP_OFF = ESC + "?7l";
    public static final String AUTOWRAP_ON  = ESC + "?7h";
    public static final String CLEAR        = ESC + BG_RGB + ESC + "2J" + ESC + "H";

    // ── Color palette ──
    public static final String DIM     = ESC + "38;2;133;146;137m";
    public static final String CYAN    = ESC + "38;2;131;192;146m";
    public static final String BLUE    = ESC + "38;2;127;187;179m";
    public static final String GREEN   = ESC + "38;2;166;180;101m";
    public static final String YELLOW  = ESC + "38;2;219;188;127m";
    public static final String MAGENTA = ESC + "38;2;214;153;182m";
    public static final String RED     = ESC + "38;2;230;126;128m";
    public static final String GREY    = ESC + "38;2;122;132;120m";

    // ── Syntax highlight accents ──
    public static final String TYPE_COLOR       = ESC + "38;2;131;192;146m";  // cyan-green
    public static final String ANNOTATION_COLOR = ESC + "38;2;214;153;182m";  // magenta-pink
    public static final String OPERATOR_COLOR   = ESC + "38;2;230;152;117m";  // warm orange
    public static final String BRACKET_COLOR    = ESC + "38;2;219;188;127m";  // gold

    // ── Completion popup ──
    public static final String POPUP_BG      = ESC + "48;2;55;65;72m";
    public static final String POPUP_FG      = ESC + "38;2;211;198;170m";
    public static final String POPUP_SEL_BG  = ESC + "48;2;127;187;179m";
    public static final String POPUP_SEL_FG  = ESC + "38;2;45;53;59m";
    public static final String POPUP_BORDER  = ESC + "38;2;133;146;137m";
    public static final String POPUP_DETAIL  = ESC + "38;2;122;132;120m";

    // ── UI element styles ──
    public static final String TITLE_BG  = ESC + "48;2;127;187;179m" + ESC + "38;2;45;53;59m";
    public static final String STATUS_BG = ESC + "48;2;166;180;101m" + ESC + "38;2;45;53;59m";

    public static final String BADGE_GREEN  = ESC + "48;2;166;180;101m" + ESC + "38;2;45;53;59m";
    public static final String BADGE_YELLOW = ESC + "48;2;219;188;127m" + ESC + "38;2;45;53;59m";
    public static final String BADGE_RED    = ESC + "48;2;230;126;128m" + ESC + "38;2;45;53;59m";

    // ── Inverse video ──
    public static final String INVERSE_ON  = "\u001b[7m";
    public static final String INVERSE_OFF = "\u001b[27m";

    /** Panel border color for a given panel index. */
    public static String panelColor(int panel) {
        return switch (panel) {
            case 0 -> CYAN;
            case 1 -> GREEN;
            case 2 -> YELLOW;
            case 3 -> MAGENTA;
            default -> DIM;
        };
    }

    /** Difficulty badge string. */
    public static String difficultyBadge(String difficulty) {
        return switch (difficulty) {
            case "Easy"   -> BADGE_GREEN  + " Easy "   + TITLE_BG;
            case "Medium" -> BADGE_YELLOW + " Medium " + TITLE_BG;
            case "Hard"   -> BADGE_RED    + " Hard "   + TITLE_BG;
            default       -> " " + difficulty + " ";
        };
    }
}
