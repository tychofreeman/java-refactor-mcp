package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

/** Where syntax nodes are in their source text. */
final class Nodes {

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
}
