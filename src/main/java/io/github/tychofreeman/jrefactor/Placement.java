package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;

/**
 * Where a member goes in a type by its kind, as {@code add_declaration} and the tools that add a
 * member put it (the user, 2026-11-07: layout is the tool's): a field after the fields, a
 * constructor after the constructors or fields, the rest at the end.
 */
final class Placement {

  private Placement() {}

  /** 0 for a field or initializer, 1 for a constructor, 2 for any other member. */
  static int rank(BodyDeclaration d) {
    if (d instanceof FieldDeclaration || d instanceof Initializer) {
      return 0;
    }
    return d instanceof MethodDeclaration m && m.isConstructor() ? 1 : 2;
  }

  /**
   * Where a member of {@code rank} goes in {@code type}: after its last member of that rank or
   * lower; for a field or constructor with none, before its first member; otherwise its end.
   */
  static Place place(AbstractTypeDeclaration type, int rank) {
    if (rank == 2) {
      return new Place(null, false);
    }
    BodyDeclaration last = null;
    for (Object o : type.bodyDeclarations()) {
      if (Placement.rank((BodyDeclaration) o) <= rank) {
        last = (BodyDeclaration) o;
      }
    }
    if (last != null) {
      return new Place(last, false);
    }
    return type.bodyDeclarations().isEmpty()
        ? new Place(null, false)
        : new Place((BodyDeclaration) type.bodyDeclarations().get(0), true);
  }
}
