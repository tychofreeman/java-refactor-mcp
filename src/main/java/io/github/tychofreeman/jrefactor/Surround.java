package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;

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
      List<String> thrown = thrown(statements);
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

  /** The checked exceptions {@code statements} throw and do not catch among themselves. */
  private static List<String> thrown(List<Statement> statements) {
    List<ITypeBinding> types = new ArrayList<>();
    for (Statement s : statements) {
      for (ASTNode n : Nodes.all(s, ASTNode.class)) {
        List<ITypeBinding> from = new ArrayList<>();
        IMethodBinding m =
            n instanceof MethodInvocation i
                ? i.resolveMethodBinding()
                : n instanceof SuperMethodInvocation i
                    ? i.resolveMethodBinding()
                    : n instanceof ClassInstanceCreation c ? c.resolveConstructorBinding() : null;
        if (m != null) {
          from.addAll(List.of(m.getExceptionTypes()));
        }
        if (n instanceof ThrowStatement t && t.getExpression().resolveTypeBinding() != null) {
          from.add(t.getExpression().resolveTypeBinding());
        }
        for (ITypeBinding t : from) {
          if (checked(t) && !caught(n, s, t) && types.stream().noneMatch(t::isSubTypeCompatible)) {
            types.removeIf(other -> other.isSubTypeCompatible(t));
            types.add(t);
          }
        }
      }
    }
    List<String> out = new ArrayList<>();
    for (ITypeBinding t : types) {
      out.add(Extract.typeName(t));
    }
    return out;
  }

  /** Whether a try within {@code top}, around {@code n}, catches {@code t}. */
  static boolean caught(ASTNode n, ASTNode top, ITypeBinding t) {
    ASTNode child = n;
    for (ASTNode p = n.getParent(); p != null && child != top; p = p.getParent()) {
      if (p instanceof TryStatement ts && ts.getBody() == child) {
        for (Object o : ts.catchClauses()) {
          Type caught = ((CatchClause) o).getException().getType();
          List<Type> alternatives = new ArrayList<>();
          if (caught instanceof UnionType u) {
            for (Object a : u.types()) {
              alternatives.add((Type) a);
            }
          } else {
            alternatives.add(caught);
          }
          for (Type a : alternatives) {
            ITypeBinding b = a.resolveBinding();
            if (b != null && t.isSubTypeCompatible(b)) {
              return true;
            }
          }
        }
      }
      child = p;
    }
    return false;
  }

  static boolean checked(ITypeBinding t) {
    for (ITypeBinding s = t; s != null; s = s.getSuperclass()) {
      String q = s.getQualifiedName();
      if (q.equals("java.lang.RuntimeException") || q.equals("java.lang.Error")) {
        return false;
      }
    }
    return true;
  }
}
