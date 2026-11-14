package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;

/**
 * Indentation: how far a line is indented, a file's step, and re-indenting lines moved from one
 * place to another. The one home for the rules every tool that writes code lines up by.
 */
final class Indent {

  private Indent() {}

  /**
   * The smallest nonzero indentation of a code line in {@code text}, or 0 when nothing is indented.
   * A comment's continuation line ({@code " * text"}) is aligned to its star, not to a step.
   */
  static int step(String text) {
    int step = Integer.MAX_VALUE;
    for (String line : text.split("\n")) {
      String code = line.stripLeading();
      int n = line.length() - code.length();
      if (n > 0 && !code.isEmpty() && !starred(line)) {
        step = Math.min(step, n);
      }
    }
    return step == Integer.MAX_VALUE ? 0 : step;
  }

  /**
   * {@code moved}, a member's source whose first line starts at the member and whose last line is
   * at its old indentation, with its other lines moved to {@code indent}: each step of the old
   * indentation inside the member becomes {@code step} spaces (as it was when {@code step} is 0). A
   * comment's continuation line ({@code " * text"}) is one space past a step, aligned to its star.
   */
  static String restep(String moved, String indent, int step) {
    String[] lines = moved.split("\n", -1);
    if (lines.length < 2) {
      return moved;
    }
    String last = lines[lines.length - 1];
    int old = width(last);
    int oldStep = Integer.MAX_VALUE;
    for (int i = 1; i < lines.length; i++) {
      String l = lines[i];
      int n = width(l);
      if (!l.isBlank() && n > old && !starred(l)) {
        oldStep = Math.min(oldStep, n - old);
      }
    }
    StringBuilder out = new StringBuilder(lines[0]);
    for (int i = 1; i < lines.length; i++) {
      String l = lines[i];
      out.append('\n');
      int n = width(l);
      if (l.isBlank() || n < old) {
        out.append(l.isBlank() ? "" : l);
        continue;
      }
      int extra = n - old;
      int star = starred(l) ? 1 : 0;
      int scaled =
          step > 0 && oldStep != Integer.MAX_VALUE && (extra - star) % oldStep == 0
              ? (extra - star) / oldStep * step + star
              : extra;
      out.append(indent).append(" ".repeat(scaled)).append(l.stripLeading());
    }
    return out.toString();
  }

  /** {@code lines} with their common indentation replaced by {@code to}. */
  static String reindent(String lines, String to) {
    String[] each = lines.split("\n", -1);
    int common = Integer.MAX_VALUE;
    for (String l : each) {
      if (!l.isBlank()) {
        common = Math.min(common, width(l));
      }
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < each.length; i++) {
      String l = each[i];
      out.append(l.isBlank() ? "" : to + l.substring(common));
      if (i < each.length - 1) {
        out.append('\n');
      }
    }
    return out.toString();
  }

  /** The number of spaces and tabs {@code line} starts with. */
  static int width(String line) {
    return line.length() - line.stripLeading().length();
  }

  /** {@link #step(String)}, or {@code fallback} when nothing in {@code text} is indented. */
  static int step(String text, int fallback) {
    int step = step(text);
    return step > 0 ? step : fallback;
  }

  /**
   * The indentation of {@code type}'s members: that of its first member on a line of its own, else
   * the type's own and one step more, the file's step or {@code fallback} when the file shows none.
   */
  static String member(String text, AbstractTypeDeclaration type, int fallback) {
    for (Object o : type.bodyDeclarations()) {
      int start = ((ASTNode) o).getStartPosition();
      String before = text.substring(Lines.start(text, start), start);
      if (before.isBlank()) {
        return before;
      }
    }
    return Lines.indentOf(text, type.getStartPosition()) + " ".repeat(step(text, fallback));
  }

  /** Whether {@code line} continues a comment: its code starts with a star. */
  private static boolean starred(String line) {
    return line.stripLeading().startsWith("*");
  }
}
