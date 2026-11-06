package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;

record IndentShift(int start, int end, int by, boolean whole) {
  /**
   * Where {@code old} is in {@code text} with the indentation of each of its non-blank lines off by
   * one amount ({@code by}: the file's minus old's, in spaces), when it matches so exactly once;
   * null otherwise. Spacing within a line must match. {@code whole} says the match starts at a
   * line's start (old's first line was indented), not inside one.
   */
  static IndentShift find(String text, String old) {
    String[] o = old.split("\n", -1);
    String[] f = text.split("\n", -1);
    int[] starts = new int[f.length];
    for (int i = 1; i < f.length; i++) {
      starts[i] = starts[i - 1] + f[i - 1].length() + 1;
    }
    List<IndentShift> found = new ArrayList<>();
    for (int i = 0; i + o.length <= f.length; i++) {
      IndentShift s = at(f, starts, i, o);
      if (s != null) {
        found.add(s);
      }
    }
    return found.size() == 1 ? found.get(0) : null;
  }

  /** Whether {@code at} is inside its line's indentation: after its start, only spaces before. */
  static boolean insideIndent(String text, int at) {
    int start = text.lastIndexOf('\n', at - 1) + 1;
    return at > start && text.substring(start, at).isBlank();
  }

  /** The match of the lines {@code o} at line {@code i} of {@code f}, or null. */
  private static IndentShift at(String[] f, int[] starts, int i, String[] o) {
    Integer by = null;
    int start = starts[i];
    int end = start;
    boolean whole = indent(o[0]) > 0;
    for (int k = 0; k < o.length; k++) {
      String ol = o[k];
      String fl = f[i + k];
      String oc = ol.substring(indent(ol));
      String fc = fl.substring(indent(fl));
      boolean first = k == 0;
      boolean last = k == o.length - 1;
      if (oc.isBlank()) {
        if (last) {
          end = starts[i + k];
          continue;
        }
        if (!fc.isBlank()) {
          return null;
        }
        continue;
      }
      boolean matches;
      if (o.length == 1) {
        matches = whole && fc.startsWith(oc);
      } else if (first) {
        matches = whole ? fc.equals(oc) : fc.endsWith(oc);
      } else if (last) {
        matches = fc.startsWith(oc);
      } else {
        matches = fc.equals(oc);
      }
      if (!matches) {
        return null;
      }
      if (first && !whole) {
        start = starts[i] + fl.length() - oc.length();
      } else {
        int d = indent(fl) - indent(ol);
        if (by != null && by != d) {
          return null;
        }
        by = d;
      }
      if (last) {
        end = starts[i + k] + indent(fl) + oc.length();
      }
    }
    return by == null || by == 0 ? null : new IndentShift(start, end, by, whole);
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
