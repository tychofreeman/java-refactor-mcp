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

  /** A member's rank in layout: a field or initializer, a constructor, or any other member. */
  static Rank rank(BodyDeclaration d) {
    if (d instanceof FieldDeclaration || d instanceof Initializer) {
      return Rank.FIELD;
    }
    return d instanceof MethodDeclaration m && m.isConstructor() ? Rank.CONSTRUCTOR : Rank.OTHER;
  }

  /**
   * Where a member of {@code rank} goes in {@code type}: after its last member of that rank or
   * lower; for a field or constructor with none, before its first member; otherwise its end.
   */
  static Place place(AbstractTypeDeclaration type, Rank rank) {
    if (rank == Rank.OTHER) {
      return new Place(null, false);
    }
    BodyDeclaration last = null;
    for (Object o : type.bodyDeclarations()) {
      if (Placement.rank((BodyDeclaration) o).compareTo(rank) <= 0) {
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

  /**
   * A member's kind as layout orders it: fields and initializers, then constructors, then the rest.
   */
  enum Rank {
    FIELD,
    CONSTRUCTOR,
    OTHER
  }
}
