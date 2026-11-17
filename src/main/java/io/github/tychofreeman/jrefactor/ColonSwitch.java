package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * A switch statement of old-style cases ({@code case RED: return "r";}) over an enum, read as the
 * switch expression of arrow cases it means ({@code return switch (c) { case RED -> "r"; ... };}),
 * for {@link EnumSwitch}: each group of cases must end in {@code return} with a value or {@code
 * throw}, and every {@code return} leaving it becomes the group's value.
 */
final class ColonSwitch {
  private ColonSwitch() {}

  /** The file with the switch rewritten, the switch expression in it, and what the result says. */
  record Read(Opened opened, SwitchExpression node, String note) {}

  /** One group: the labels that share its statements, and the statements. */
  private record Group(List<SwitchCase> labels, List<Statement> statements) {
    boolean isDefault() {
      return labels.stream().anyMatch(SwitchCase::isDefault);
    }

    String label(String text) {
      if (isDefault()) {
        return "default";
      }
      List<String> names = new ArrayList<>();
      for (SwitchCase c : labels) {
        for (Object o : c.expressions()) {
          names.add(Nodes.source(text, (Expression) o));
        }
      }
      return String.join(", ", names);
    }
  }

  /** Whether {@code s} has a case of the old style, with a colon. */
  static boolean colon(SwitchStatement s) {
    for (Object o : s.statements()) {
      if (o instanceof SwitchCase c && !c.isSwitchLabeledRule()) {
        return true;
      }
    }
    return false;
  }

  /** The switch {@code s} over the enum {@code e}, read as a switch expression of arrow cases. */
  static Read arrows(SourceProject project, Opened opened, SwitchStatement s, ITypeBinding e)
      throws IOException {
    String text = opened.text();
    List<Group> groups = groups(s);
    checkLocals(groups, text);
    StringBuilder out =
        new StringBuilder("return switch (" + Nodes.source(text, s.getExpression()) + ") {\n");
    boolean fallback = false;
    Set<String> named = new LinkedHashSet<>();
    for (Group g : groups) {
      String label = g.label(text);
      checkEnds(g, label, e);
      fallback |= g.isDefault();
      for (SwitchCase c : g.labels()) {
        for (Object o : c.expressions()) {
          if (o instanceof Name n && n.resolveBinding() instanceof IVariableBinding b) {
            named.add(b.getName());
          }
        }
      }
      out.append("case ").append(label).append(" -> ").append(arm(g, s, text)).append("\n");
    }
    out.append("};");
    List<TextEdit> edits = new ArrayList<>();
    edits.add(new TextEdit(s.getStartPosition(), Nodes.end(s), out.toString()));
    String note = "; its old-style cases were read as arrow cases, each return as the case's value";
    if (!fallback) {
      List<String> missing = new ArrayList<>();
      for (IVariableBinding f : e.getDeclaredFields()) {
        if (f.isEnumConstant() && !named.contains(f.getName())) {
          missing.add(f.getName());
        }
      }
      if (!missing.isEmpty()) {
        throw new Refusal(
            Names.listed(missing)
                + (missing.size() == 1 ? " has" : " have")
                + " no case and "
                + (missing.size() == 1 ? "runs" : "run")
                + " the code after the switch, which cannot move into "
                + e.getName()
                + ". Add a default case that does what that code does (add_case), then call"
                + " again.");
      }
      if (s.getParent() instanceof Block b) {
        List<?> list = b.statements();
        int i = list.indexOf(s);
        if (i < list.size() - 1) {
          edits.add(new TextEdit(Nodes.end(s), Nodes.end((ASTNode) list.get(list.size() - 1)), ""));
          note += "; the statements after the switch, which no constant reached, are removed";
        }
      }
    }
    String rewritten = TextEdit.apply(text, edits, 0);
    CompilationUnit unit = Jdt.parse(project, opened.file(), rewritten);
    int at = s.getStartPosition() + "return ".length();
    SwitchExpression node =
        Nodes.all(unit, SwitchExpression.class).stream()
            .filter(x -> x.getStartPosition() == at)
            .findFirst()
            .orElseThrow();
    return new Read(new Opened(project, opened.rel(), opened.file(), rewritten, unit), node, note);
  }

  /** The groups of {@code s}: labels with no statement between them share the next ones. */
  private static List<Group> groups(SwitchStatement s) {
    List<Group> out = new ArrayList<>();
    Group current = null;
    for (Object o : s.statements()) {
      if (o instanceof SwitchCase c) {
        if (current == null || !current.statements().isEmpty()) {
          current = new Group(new ArrayList<>(), new ArrayList<>());
          out.add(current);
        }
        current.labels().add(c);
      } else {
        current.statements().add((Statement) o);
      }
    }
    return out;
  }

  /**
   * Refuses a group that can finish, running on into the next case or past the switch, and a jump
   * that leaves the group other than a return with a value.
   */
  private static void checkEnds(Group g, String label, ITypeBinding e) {
    String end =
        "; each case must end in return with a value or throw to move into " + e.getName() + ".";
    for (Statement top : g.statements()) {
      for (Statement j : Nodes.all(top, Statement.class)) {
        boolean leaves =
            (j instanceof BreakStatement || j instanceof ContinueStatement)
                && Effects.exits(j, top);
        boolean bare =
            j instanceof ReturnStatement r && r.getExpression() == null && Effects.exits(j, top);
        if (leaves || bare) {
          throw new Refusal(
              "the case of "
                  + label
                  + " has "
                  + j.toString().strip()
                  + (bare ? ", which gives no value" : ", which leaves the switch")
                  + end);
        }
      }
    }
    if (g.statements().isEmpty()
        || Effects.completesNormally(g.statements().get(g.statements().size() - 1))) {
      throw new Refusal(
          "the case of "
              + label
              + " can finish without a return or throw, running on into the next case or past"
              + " the switch"
              + end);
    }
  }

  /** Refuses a local declared in one group and used in another: arrow cases each have their own. */
  private static void checkLocals(List<Group> groups, String text) {
    Map<String, Integer> declaredIn = new HashMap<>();
    for (int i = 0; i < groups.size(); i++) {
      for (Statement st : groups.get(i).statements()) {
        if (st instanceof VariableDeclarationStatement v) {
          for (Object f : v.fragments()) {
            if (((VariableDeclarationFragment) f).resolveBinding() instanceof IVariableBinding b) {
              declaredIn.put(b.getKey(), i);
            }
          }
        }
      }
    }
    for (int i = 0; i < groups.size(); i++) {
      for (Statement st : groups.get(i).statements()) {
        for (SimpleName n : Nodes.all(st, SimpleName.class)) {
          Integer d =
              n.resolveBinding() instanceof IVariableBinding b ? declaredIn.get(b.getKey()) : null;
          if (d != null && d != i) {
            throw new Refusal(
                n.getIdentifier()
                    + " is declared in the case of "
                    + groups.get(d).label(text)
                    + " and used in the case of "
                    + groups.get(i).label(text)
                    + "; as arrow cases each would have its own "
                    + n.getIdentifier()
                    + ". Declare it in each case that uses it, then call again.");
          }
        }
      }
    }
  }

  /**
   * The arrow case's body: the value of a lone return, a lone throw, or else a block of the
   * statements, each return leaving the switch a yield.
   */
  private static String arm(Group g, SwitchStatement s, String text) {
    List<Statement> st = g.statements();
    if (st.size() == 1 && st.get(0) instanceof ReturnStatement r) {
      return Nodes.source(text, r.getExpression()) + ";";
    }
    if (st.size() == 1 && st.get(0) instanceof ThrowStatement t) {
      return Nodes.source(text, t);
    }
    int base = st.get(0).getStartPosition();
    List<TextEdit> edits = new ArrayList<>();
    for (Statement top : st) {
      for (ReturnStatement r : Nodes.all(top, ReturnStatement.class)) {
        if (Effects.exits(r, s)) {
          int at = r.getStartPosition() - base;
          edits.add(new TextEdit(at, at + "return".length(), "yield"));
        }
      }
    }
    String body = TextEdit.apply(text.substring(base, Nodes.end(st.get(st.size() - 1))), edits, 0);
    // The first statement keeps the indentation of the lines after it, so the body re-indents.
    int nl = body.indexOf('\n');
    String pad = nl < 0 ? "" : body.substring(nl + 1).replaceFirst("(?s)^([ \\t]*).*", "$1");
    return "{\n" + pad + body + "\n}";
  }
}
