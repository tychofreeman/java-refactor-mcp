package io.github.tychofreeman.jrefactor;

import java.util.List;
import java.util.stream.Collectors;

/** Line numbers and offsets in Java text, and how a refusal or a result names lines. */
final class Lines {

  private Lines() {}

  /** The 1-based line of {@code offset} in {@code text}. */
  static int number(CharSequence text, int offset) {
    int line = 1;
    for (int i = 0; i < offset && i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        line++;
      }
    }
    return line;
  }

  /** The offset of the first character of the line that holds {@code offset}. */
  static int start(String text, int offset) {
    int i = offset;
    while (i > 0 && text.charAt(i - 1) != '\n') {
      i--;
    }
    return i;
  }

  /** The offset just past the line ending of the line that holds {@code offset}. */
  static int end(String text, int offset) {
    int i = text.indexOf('\n', offset);
    return i < 0 ? text.length() : i + 1;
  }

  /** The offset of the first character of 1-based {@code line}, or a refusal past the end. */
  static int offsetOf(String text, int line) {
    int offset = 0;
    for (int i = 1; i < line; i++) {
      int next = text.indexOf('\n', offset);
      if (next < 0) {
        throw new Refusal("line " + line + " is past the end of the file.");
      }
      offset = next + 1;
    }
    return offset;
  }

  /** The spaces and tabs that start the line {@code at} is on. */
  static String indentOf(String text, int at) {
    int start = start(text, at);
    int end = start;
    while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
      end++;
    }
    return text.substring(start, end);
  }

  /** "line 4" for one line, "lines 4-7" for a range. */
  static String range(int first, int last) {
    return first == last ? "line " + first : "lines " + first + "-" + last;
  }

  /** "line 4" or "lines 4, 7, 9". */
  static String list(List<Integer> lines) {
    return (lines.size() == 1 ? "line " : "lines ")
        + lines.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  /**
   * The offset of 1-based {@code line} and {@code column}, or a refusal for one before the file or
   * past the end of the file or the line.
   */
  static int offsetOf(String text, int line, int column) {
    if (line < 1 || column < 1) {
      throw new Refusal(
          "line and column start at 1; got line " + line + ", column " + column + ".");
    }
    int start = offsetOf(text, line);
    int end = text.indexOf('\n', start);
    if (start + column - 1 > (end < 0 ? text.length() : end)) {
      throw new Refusal("column " + column + " is past the end of line " + line + ".");
    }
    return start + column - 1;
  }
}
