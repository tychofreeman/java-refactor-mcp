package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Statement;

final class Surround {
  private Surround() {}

  /** The heads a block statement can have that surround takes. */
  private static final Pattern HEAD =
      Pattern.compile("try|(if|for|while|synchronized)\\s*\\(.*\\)", Pattern.DOTALL);

  /**
   * {@code body} (whole lines of {@code statements}) inside the block statement headed {@code
   * head}, one indentation {@code step} in. A {@code try} catches and rethrows the checked
   * exceptions the statements throw, as {@code name} unless {@code used} has it; with none, it has
   * an empty {@code finally}.
   */
  static String wrap(
      String head, String body, String step, List<Statement> statements, String used) {
    String h = head.strip();
    if (!HEAD.matcher(h).matches()) {
      throw new Refusal(
          "surround is the head of a block statement: try, if (cond), for (...), while (cond) or"
              + " synchronized (lock); "
              + h
              + " is none of these.");
    }
    StringBuilder out =
        new StringBuilder(h).append(" {\n").append(Primitives.indent(body, step)).append("\n}");
    if (h.equals("try")) {
      List<String> thrown = new ArrayList<>();
      for (ITypeBinding t : Exceptions.uncaught(statements)) {
        thrown.add(Extract.typeName(t));
      }
      if (thrown.isEmpty()) {
        out.append(" finally {\n}");
      } else {
        String e = "e";
        for (int n = 2; Pattern.compile("\\b" + e + "\\b").matcher(used).find(); n++) {
          e = "e" + n;
        }
        out.append(" catch (")
            .append(String.join(" | ", thrown))
            .append(" ")
            .append(e)
            .append(") {\n")
            .append(step)
            .append("throw ")
            .append(e)
            .append(";\n}");
      }
    }
    return out.toString();
  }
}
