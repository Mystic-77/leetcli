package com.leetcli.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates a single editable text buffer with cursor, scroll, and selection state.
 * Reused for both the code editor and test case editor panels.
 */
public class EditorState {

    private List<String> lines;
    private int curLine = 0;
    private int curCol = 0;
    private int scrollY = 0;
    private int scrollX = 0;
    private int anchorLine = -1;
    private int anchorCol = -1;

    public EditorState(String content) {
        this.lines = new ArrayList<>(List.of(content.replace("\t", "    ").split("\n", -1)));
    }

    // ── Getters ──
    public List<String> getLines()  { return lines; }
    public int getCurLine()         { return curLine; }
    public int getCurCol()          { return curCol; }
    public int getScrollY()         { return scrollY; }
    public int getScrollX()         { return scrollX; }
    public int getAnchorLine()      { return anchorLine; }
    public int getAnchorCol()       { return anchorCol; }
    public boolean hasSelection()   { return anchorLine != -1; }

    public String getLine(int idx) {
        return (idx >= 0 && idx < lines.size()) ? lines.get(idx) : "";
    }

    public String getText() { return String.join("\n", lines); }

    // ── Reset ──
    public void reset(String content) {
        this.lines = new ArrayList<>(List.of(content.replace("\t", "    ").split("\n", -1)));
        curLine = 0; curCol = 0; scrollY = 0; scrollX = 0;
        anchorLine = -1; anchorCol = -1;
    }

    // ── Selection ──
    private void startSelection(boolean shift) {
        if (shift && anchorLine == -1) { anchorLine = curLine; anchorCol = curCol; }
        if (!shift) { anchorLine = -1; anchorCol = -1; }
    }

    public boolean isSelected(int y, int x) {
        if (anchorLine == -1) return false;
        int sy = curLine, sx = curCol, ey = anchorLine, ex = anchorCol;
        if (sy > ey || (sy == ey && sx > ex)) { sy = anchorLine; sx = anchorCol; ey = curLine; ex = curCol; }
        if (y < sy || y > ey) return false;
        if (sy == ey) return x >= sx && x < ex;
        if (y == sy) return x >= sx;
        if (y == ey) return x < ex;
        return true;
    }

    public void deleteSelection() {
        if (anchorLine == -1) return;
        int sy = curLine, sx = curCol, ey = anchorLine, ex = anchorCol;
        if (sy > ey || (sy == ey && sx > ex)) { sy = anchorLine; sx = anchorCol; ey = curLine; ex = curCol; }
        String before = lines.get(sy).substring(0, sx);
        String after = lines.get(ey).substring(Math.min(ex, lines.get(ey).length()));
        for (int i = ey; i > sy; i--) lines.remove(i);
        lines.set(sy, before + after);
        curLine = sy; curCol = sx; anchorLine = -1; anchorCol = -1;
    }

    // ── Cursor movement ──
    public void moveUp(boolean shift) {
        startSelection(shift);
        if (curLine > 0) curLine--;
        curCol = Math.min(curCol, lines.get(curLine).length());
    }

    public void moveDown(boolean shift) {
        startSelection(shift);
        if (curLine < lines.size() - 1) curLine++;
        curCol = Math.min(curCol, lines.get(curLine).length());
    }

    public void moveLeft(boolean shift) {
        startSelection(shift);
        if (curCol > 0) curCol--;
        else if (curLine > 0) { curLine--; curCol = lines.get(curLine).length(); }
    }

    public void moveRight(boolean shift) {
        startSelection(shift);
        if (curCol < lines.get(curLine).length()) curCol++;
        else if (curLine < lines.size() - 1) { curLine++; curCol = 0; }
    }

    public void home(boolean shift) { startSelection(shift); curCol = 0; }

    public void end(boolean shift) { startSelection(shift); curCol = lines.get(curLine).length(); }

    // ── Editing ──
    public void insertChar(char c) {
        if (anchorLine != -1) deleteSelection();
        String line = lines.get(curLine);
        lines.set(curLine, line.substring(0, curCol) + c + line.substring(curCol));
        curCol++;
    }

    public void insertTab() {
        if (anchorLine != -1) deleteSelection();
        String line = lines.get(curLine);
        lines.set(curLine, line.substring(0, curCol) + "    " + line.substring(curCol));
        curCol += 4;
    }

    public void enter() {
        if (anchorLine != -1) deleteSelection();
        String line = lines.get(curLine);
        lines.set(curLine, line.substring(0, curCol));
        lines.add(curLine + 1, line.substring(curCol));
        curLine++; curCol = 0;
    }

    public void backspace() {
        if (anchorLine != -1) { deleteSelection(); return; }
        if (curCol > 0) {
            String line = lines.get(curLine);
            lines.set(curLine, line.substring(0, curCol - 1) + line.substring(curCol));
            curCol--;
        } else if (curLine > 0) {
            String current = lines.remove(curLine);
            curLine--;
            curCol = lines.get(curLine).length();
            lines.set(curLine, lines.get(curLine) + current);
        }
    }

    public void delete() {
        if (anchorLine != -1) { deleteSelection(); return; }
        String line = lines.get(curLine);
        if (curCol < line.length()) {
            lines.set(curLine, line.substring(0, curCol) + line.substring(curCol + 1));
        } else if (curLine < lines.size() - 1) {
            lines.set(curLine, line + lines.remove(curLine + 1));
        }
    }

    // ── Scrolling ──
    public void ensureVisible(int viewW, int viewH) {
        if (curLine < scrollY) scrollY = curLine;
        else if (curLine >= scrollY + viewH) scrollY = curLine - viewH + 1;
        if (curCol < scrollX) scrollX = curCol;
        else if (curCol >= scrollX + viewW) scrollX = curCol - viewW + 1;
    }

    public void scroll(int delta, int maxLines) {
        scrollY = Math.max(0, Math.min(scrollY + delta, Math.max(0, maxLines - 1)));
    }
}
