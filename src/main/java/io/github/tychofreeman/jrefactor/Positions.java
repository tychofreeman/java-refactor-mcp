package io.github.tychofreeman.jrefactor;

/** Converts the 1-based line and column that tools take into a character offset. */
final class Positions {

    private Positions() {
    }

    static int offset(String source, int line, int column) {
        if (line < 1 || column < 1) {
            throw new Refusal("line and column start at 1; got line " + line + ", column " + column + ".");
        }
        int lineStart = 0;
        for (int current = 1; current < line; current++) {
            int newline = source.indexOf('\n', lineStart);
            if (newline < 0) {
                throw new Refusal("line " + line + " is past the end of the file.");
            }
            lineStart = newline + 1;
        }
        int lineEnd = source.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        if (lineStart + column - 1 > lineEnd) {
            throw new Refusal("column " + column + " is past the end of line " + line + ".");
        }
        return lineStart + column - 1;
    }
}
