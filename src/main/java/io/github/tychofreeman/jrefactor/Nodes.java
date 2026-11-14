package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;

/** Where syntax nodes are in their source text. */
final class Nodes {

  /** The most characters {@link #show} gives. */
  private static final int SHOWN = 60;

  private Nodes() {}

  /** The offset just past {@code node}. */
  static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }

  /** Whether {@code inner} lies within {@code outer}; false for no {@code outer}. */
  static boolean within(ASTNode inner, ASTNode outer) {
    if (outer == null) {
      return false;
    }
    int start = outer.getStartPosition();
    return inner.getStartPosition() >= start && end(inner) <= start + outer.getLength();
  }

  /**
   * The 1-based columns where {@code nodes} start on the line at {@code lineStart}: "12 and 26".
   */
  static String columns(List<? extends ASTNode> nodes, int lineStart) {
    List<String> out = new ArrayList<>();
    for (ASTNode n : nodes) {
      out.add(String.valueOf(n.getStartPosition() - lineStart + 1));
    }
    if (out.size() == 1) {
      return out.get(0);
    }
    return String.join(", ", out.subList(0, out.size() - 1)) + " and " + out.get(out.size() - 1);
  }

  /** The nodes of {@code type} in {@code root}, itself included, in source order. */
  static <T extends ASTNode> List<T> all(ASTNode root, Class<T> type) {
    List<T> out = new ArrayList<>();
    root.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode n) {
            if (type.isInstance(n)) {
              out.add(type.cast(n));
            }
          }
        });
    return out;
  }

  /**
   * Whether {@code x} is written: an assignment's target, or what ++ or -- changes, also inside
   * parentheses ({@code (x)++}).
   */
  static boolean written(Expression x) {
    ASTNode at = x;
    while (at.getParent() instanceof ParenthesizedExpression paren) {
      at = paren;
    }
    ASTNode p = at.getParent();
    return (p instanceof Assignment a && a.getLeftHandSide() == at)
        || (p instanceof PostfixExpression)
        || (p instanceof PrefixExpression pre
            && (pre.getOperator() == PrefixExpression.Operator.INCREMENT
                || pre.getOperator() == PrefixExpression.Operator.DECREMENT));
  }

  /**
   * {@code node}'s source text on one line, cut to {@link #SHOWN} characters: what a refusal
   * quotes, so the model can find it in the file. The node's own {@code toString} reformats it,
   * over several lines for a lambda or an anonymous class.
   */
  static String show(ASTNode node) {
    String source = node.getRoot() instanceof CompilationUnit u ? Jdt.sourceOf(u) : null;
    String text =
        source != null && end(node) <= source.length()
            ? source.substring(node.getStartPosition(), end(node))
            : node.toString();
    String line =
        text.strip()
            .replaceAll("\\(\\s*\\R\\s*", "(")
            .replaceAll("\\s*\\R\\s*([.)])", "$1")
            .replaceAll("\\s*\\R\\s*", " ");
    return line.length() <= SHOWN ? line : line.substring(0, SHOWN - 3) + "...";
  }

  /** The nearest ancestor of {@code n} of {@code kind}, not {@code n} itself, or null. */
  static <T extends ASTNode> T ancestor(ASTNode n, Class<T> kind) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (kind.isInstance(p)) {
        return kind.cast(p);
      }
    }
    return null;
  }

  /** {@code node} or its nearest ancestor of {@code type}, or null. */
  static <T> T ancestorOrSelf(ASTNode node, Class<T> type) {
    for (ASTNode n = node; n != null; n = n.getParent()) {
      if (type.isInstance(n)) {
        return type.cast(n);
      }
    }
    return null;
  }

  /** The type, named or anonymous, whose body holds {@code n}, or null. */
  static ITypeBinding typeAround(ASTNode n) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t) {
        return t.resolveBinding();
      }
      if (p instanceof AnonymousClassDeclaration a) {
        return a.resolveBinding();
      }
    }
    return null;
  }

  /** The nearest {@code type} around {@code n}, or null past a lambda or an anonymous class. */
  static <T extends ASTNode> T enclosing(ASTNode n, Class<T> type) {
    for (ASTNode p = n.getParent(); p != null; p = p.getParent()) {
      if (type.isInstance(p)) {
        return type.cast(p);
      }
      if (p instanceof LambdaExpression || p instanceof AnonymousClassDeclaration) {
        return null;
      }
      if (p instanceof CompilationUnit) {
        return null;
      }
    }
    return null;
  }

  /** {@code e} without the parentheses around it. */
  static Expression unparenthesized(Expression e) {
    while (e instanceof ParenthesizedExpression p) {
      e = p.getExpression();
    }
    return e;
  }

  /** The source of {@code n} in {@code text}. */
  static String source(String text, ASTNode n) {
    return text.substring(n.getStartPosition(), Nodes.end(n));
  }
}
