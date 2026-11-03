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

  /** "line 4" for one line, "lines 4-7" for a range. */
  static String range(int first, int last) {
    return first == last ? "line " + first : "lines " + first + "-" + last;
  }

  /** "line 4" or "lines 4, 7, 9". */
  static String list(List<Integer> lines) {
    return (lines.size() == 1 ? "line " : "lines ")
        + lines.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }
}
