package com.leetcli.tui;

import org.jline.utils.NonBlockingReader;
import java.io.IOException;

/**
 * Parses raw terminal input (ANSI/CSI escape sequences) into high-level key events.
 */
public final class InputHandler {

    private InputHandler() {}

    public enum Action {
        ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
        SHIFT_UP, SHIFT_DOWN, SHIFT_LEFT, SHIFT_RIGHT,
        CTRL_UP, CTRL_DOWN, CTRL_LEFT, CTRL_RIGHT,
        HOME, END, SHIFT_HOME, SHIFT_END,
        DELETE, BACKSPACE, TAB, ENTER,
        SAVE, RUN, SUBMIT, LOAD_FILE, SWITCH_LANG,
        QUIT, CHAR, NONE, CTRL_SPACE, ESCAPE
    }

    public record KeyEvent(Action action, char ch) {
        public static KeyEvent of(Action a) { return new KeyEvent(a, '\0'); }
        public static KeyEvent ofChar(char c) { return new KeyEvent(Action.CHAR, c); }
    }

    /** Read a key event with a 100ms timeout so the main loop can poll for resize. */
    public static KeyEvent read(NonBlockingReader reader) throws IOException {
        int c = reader.read(100);  // 100ms timeout — returns -2 on timeout
        if (c == -1 || c == -2) return KeyEvent.of(Action.NONE);

        // Ctrl+Space sends NUL (ASCII 0)
        if (c == 0) return KeyEvent.of(Action.CTRL_SPACE);

        if (c == 27) {
            int c2 = reader.read(200);
            if (c2 == -1 || c2 == -2) return KeyEvent.of(Action.ESCAPE);
            if (c2 == 'O') {
                int c3 = reader.read();
                if (c3 <= 0) return KeyEvent.of(Action.NONE);
                return switch ((char) c3) {
                    case 'A' -> KeyEvent.of(Action.ARROW_UP);
                    case 'B' -> KeyEvent.of(Action.ARROW_DOWN);
                    case 'C' -> KeyEvent.of(Action.ARROW_RIGHT);
                    case 'D' -> KeyEvent.of(Action.ARROW_LEFT);
                    case 'H' -> KeyEvent.of(Action.HOME);
                    case 'F' -> KeyEvent.of(Action.END);
                    default  -> KeyEvent.of(Action.NONE);
                };
            } else if (c2 == '[') {
                StringBuilder seq = new StringBuilder();
                while (true) {
                    int b = reader.read();
                    if (b <= 0) break;
                    seq.append((char) b);
                    if (b >= 0x40 && b <= 0x7E) break;
                }
                return parseCSI(seq.toString());
            }
            return KeyEvent.of(Action.NONE);
        }

        if (c == 19) return KeyEvent.of(Action.SAVE);
        if (c == '\r' || c == '\n') return KeyEvent.of(Action.ENTER);
        if (c == 127 || c == 8) return KeyEvent.of(Action.BACKSPACE);
        if (c == '\t') return KeyEvent.of(Action.TAB);
        if (c >= 32 && c < 127) return KeyEvent.ofChar((char) c);

        return KeyEvent.of(Action.NONE);
    }

    private static KeyEvent parseCSI(String seq) {
        if (seq.equals("A")) return KeyEvent.of(Action.ARROW_UP);
        if (seq.equals("B")) return KeyEvent.of(Action.ARROW_DOWN);
        if (seq.equals("C")) return KeyEvent.of(Action.ARROW_RIGHT);
        if (seq.equals("D")) return KeyEvent.of(Action.ARROW_LEFT);
        if (seq.equals("H") || seq.equals("1~")) return KeyEvent.of(Action.HOME);
        if (seq.equals("F") || seq.equals("4~")) return KeyEvent.of(Action.END);
        if (seq.equals("3~")) return KeyEvent.of(Action.DELETE);

        // Ctrl+Arrow
        if (seq.endsWith("A") && seq.contains(";5")) return KeyEvent.of(Action.CTRL_UP);
        if (seq.endsWith("B") && seq.contains(";5")) return KeyEvent.of(Action.CTRL_DOWN);
        if (seq.endsWith("C") && seq.contains(";5")) return KeyEvent.of(Action.CTRL_RIGHT);
        if (seq.endsWith("D") && seq.contains(";5")) return KeyEvent.of(Action.CTRL_LEFT);

        // Shift+Arrow
        if (seq.endsWith("A") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_UP);
        if (seq.endsWith("B") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_DOWN);
        if (seq.endsWith("C") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_RIGHT);
        if (seq.endsWith("D") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_LEFT);
        if (seq.endsWith("H") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_HOME);
        if (seq.endsWith("F") && seq.contains(";2")) return KeyEvent.of(Action.SHIFT_END);

        // Function keys
        if (seq.equals("15~")) return KeyEvent.of(Action.RUN);
        if (seq.equals("17~")) return KeyEvent.of(Action.SUBMIT);
        if (seq.equals("18~")) return KeyEvent.of(Action.LOAD_FILE);
        if (seq.equals("19~")) return KeyEvent.of(Action.SWITCH_LANG);

        return KeyEvent.of(Action.NONE);
    }
}
