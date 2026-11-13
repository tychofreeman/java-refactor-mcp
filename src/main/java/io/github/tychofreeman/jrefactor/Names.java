package io.github.tychofreeman.jrefactor;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

/** How the tools name a type in messages and stubs: its simple name with its enclosing types. */
final class Names {

  private Names() {}

  /** A type's name with its enclosing types: {@code Outer.Inner}. */
  static String typeLabel(ITypeBinding type) {
    ITypeBinding t = type.getErasure();
    return t.getDeclaringClass() == null
        ? t.getName()
        : typeLabel(t.getDeclaringClass()) + "." + t.getName();
  }

  /**
   * A name for the element of {@code of}: a plural's singular ({@code names} to {@code name},
   * {@code entries} to {@code entry}), a {@code List} suffix dropped, else {@code item}.
   */
  static String elementName(Expression of) {
    String n =
        of instanceof SimpleName s
            ? s.getIdentifier()
            : of instanceof FieldAccess f
                ? f.getName().getIdentifier()
                : of instanceof QualifiedName q ? q.getName().getIdentifier() : "";
    String singular =
        n.endsWith("List") && n.length() > 4
            ? n.substring(0, n.length() - 4)
            : n.endsWith("ies") && n.length() > 3
                ? n.substring(0, n.length() - 3) + "y"
                : n.endsWith("s") && !n.endsWith("ss") && n.length() > 1
                    ? n.substring(0, n.length() - 1)
                    : "";
    return singular.isEmpty() || !Character.isJavaIdentifierStart(singular.charAt(0))
        ? "item"
        : singular;
  }
}
