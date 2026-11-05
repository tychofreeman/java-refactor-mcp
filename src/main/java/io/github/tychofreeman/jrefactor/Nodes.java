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
