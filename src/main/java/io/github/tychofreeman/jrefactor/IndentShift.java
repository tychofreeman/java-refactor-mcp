package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record IndentShift(int start, int end, int by, boolean whole) {
  /**
   * Every place {@code old} is in {@code text} with the spacing within its lines ignored (each run
   * of spaces and tabs standing for any other, line breaks kept: gorefactor's rule), each with
   * {@code by}, the file's indentation minus old's on the first line that has both, which {@code
   * new} moves by. {@code whole} says the match starts at a line's start (old's first line was
   * indented).
   */
  static List<IndentShift> locate(String text, String old) {
    List<IndentShift> out = new ArrayList<>();
    String[] o = old.split("\n", -1);
    Matcher m = spacingIgnored(old).matcher(text);
    while (m.find()) {
      int s = m.start();
      int lineStart = text.lastIndexOf('\n', s - 1) + 1;
      boolean whole = indent(o[0]) > 0;
      int by = 0;
      String[] f = text.substring(lineStart, m.end()).split("\n", -1);
      for (int k = whole ? 0 : 1; k < o.length && k < f.length; k++) {
        if (!o[k].isBlank()) {
          by = indent(f[k]) - indent(o[k]);
          break;
        }
      }
      out.add(new IndentShift(s, m.end(), by, whole));
    }
    return out;
  }

  /**
   * A pattern for {@code text} with each run of spaces and tabs standing for any other, line breaks
   * kept (gorefactor's {@code spacingIgnored}); a run at the end takes no next line's indentation.
   */
  private static Pattern spacingIgnored(String text) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < text.length(); ) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t' && c != '\n') {
        sb.append(Pattern.quote(String.valueOf(c)));
        i++;
        continue;
      }
      int j = i;
      while (j < text.length() && " \t\n".indexOf(text.charAt(j)) >= 0) {
        j++;
      }
      long breaks = text.substring(i, j).chars().filter(x -> x == '\n').count();
      if (breaks > 0) {
        sb.append("[ \\t]*\\n".repeat((int) breaks));
        if (j < text.length()) {
          sb.append("[ \\t]*");
        }
      } else {
        sb.append("[ \\t]+");
      }
      i = j;
    }
    return Pattern.compile(sb.toString());
  }

  /** Whether {@code at} is inside its line's indentation: after its start, only spaces before. */
  static boolean insideIndent(String text, int at) {
    int start = text.lastIndexOf('\n', at - 1) + 1;
    return at > start && text.substring(start, at).isBlank();
  }

  /** The number of spaces {@code line} starts with. */
  private static int indent(String line) {
    int n = 0;
    while (n < line.length() && line.charAt(n) == ' ') {
      n++;
    }
    return n;
  }

  /**
   * {@code text} with each non-blank line indented {@code by} spaces more (or less, when negative),
   * the first line only when {@code first}.
   */
  static String shift(String text, int by, boolean first) {
    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder();
    for (int j = 0; j < lines.length; j++) {
      String line = lines[j];
      if ((j > 0 || first) && !line.isBlank()) {
        line = by > 0 ? " ".repeat(by) + line : line.substring(Math.min(-by, indent(line)));
      }
      out.append(j > 0 ? "\n" : "").append(line);
    }
    return out.toString();
  }

  /**
   * For a refusal: where in {@code text} the first line of {@code old} that it has is, spacing
   * ignored ("; its line int b() { is at line 8"), or empty.
   */
  static String hint(String text, String old) {
    String[] f = text.split("\n", -1);
    for (String ol : old.split("\n", -1)) {
      String want = String.join(" ", ol.trim().split("\\s+"));
      if (want.isEmpty()) {
        continue;
      }
      List<Integer> at = new ArrayList<>();
      for (int i = 0; i < f.length; i++) {
        if (String.join(" ", f[i].trim().split("\\s+")).contains(want)) {
          at.add(i + 1);
        }
      }
      if (!at.isEmpty()) {
        return "; its line " + want + " is at " + Lines.list(at.subList(0, Math.min(at.size(), 5)));
      }
    }
    return "";
  }
}
