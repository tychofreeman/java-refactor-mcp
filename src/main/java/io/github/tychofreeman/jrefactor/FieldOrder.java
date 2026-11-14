package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Fields in the order their initializers read them: a field an edit added, read by the initializer
 * of a field declared before it, moves to just before that field (layout is the tool's, the user,
 * 2026-11-07; issue #357).
 */
final class FieldOrder {

  private FieldOrder() {}

  /**
   * Moves one field of {@code file} that the change added (not in its text in {@code old}) and that
   * an earlier field's initializer reads, to just before that field. Returns whether it moved one;
   * called again until it does not.
   */
  static boolean fix(SourceProject project, Path file, Map<Path, String> old) throws IOException {
    if (!old.containsKey(file)) {
      return false;
    }
    String text = Files.readString(file);
    CompilationUnit unit = Jdt.parse(project, file, text);
    for (IProblem p : unit.getProblems()) {
      if (p.getID() != IProblem.ReferenceToForwardField) {
        continue;
      }
      ASTNode node = Nodes.at(unit, p);
      if (!(node instanceof SimpleName n)
          || !(n.resolveBinding() instanceof IVariableBinding v)
          || !(unit.findDeclaringNode(v) instanceof VariableDeclarationFragment f)
          || !(f.getParent() instanceof FieldDeclaration moved)
          || moved.fragments().size() != 1) {
        continue;
      }
      FieldDeclaration reader = Nodes.ancestorOrSelf(n, FieldDeclaration.class);
      if (reader == null
          || reader.getParent() != moved.getParent()
          || reader.getStartPosition() > moved.getStartPosition()
          || existed(old.get(file), (AbstractTypeDeclaration) moved.getParent(), v.getName())) {
        continue;
      }
      int from = Lines.start(text, moved.getStartPosition());
      // The blank line before it, which placing it gave it, is dropped.
      if (from > 0 && text.substring(Lines.start(text, from - 1), from).isBlank()) {
        from = Lines.start(text, from - 1);
      }
      int to = Lines.end(text, Nodes.end(moved));
      int at = Lines.start(text, reader.getStartPosition());
      String field = text.substring(Lines.start(text, moved.getStartPosition()), to);
      String without = text.substring(0, from) + text.substring(to);
      Journal.put(project, file, without.substring(0, at) + field + without.substring(at));
      return true;
    }
    return false;
  }

  /**
   * Whether {@code before}, the file's text before the change, had field {@code name} in a type
   * named as {@code type}.
   */
  private static boolean existed(String before, AbstractTypeDeclaration type, String name) {
    if (before == null) {
      return false;
    }
    for (VariableDeclarationFragment f :
        Nodes.all(Jdt.parseSyntax(before), VariableDeclarationFragment.class)) {
      if (f.getName().getIdentifier().equals(name)
          && f.getParent() instanceof FieldDeclaration d
          && d.getParent() instanceof AbstractTypeDeclaration t
          && t.getName().getIdentifier().equals(type.getName().getIdentifier())) {
        return true;
      }
    }
    return false;
  }
}
