package io.github.tychofreeman.jrefactor;

import java.util.Set;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

/**
 * How the tools name things: a type in messages and stubs, by its simple name with its enclosing
 * types, and the names they declare, which no keyword or name in scope is.
 */
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
   * {@code entries} to {@code entry}), a {@code List} suffix dropped, else {@code item}, also for a
   * keyword ({@code ints}).
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
    return singular.isEmpty() || !SourceVersion.isName(singular) ? "item" : singular;
  }

  /** {@code free(name, takenIn(loop))}. */
  static String fresh(String name, ASTNode loop) {
    return free(name, takenIn(loop));
  }

  /** The identifiers of {@code node}'s file. */
  static Set<String> takenIn(ASTNode node) {
    Set<String> taken = new java.util.HashSet<>();
    Nodes.all(node.getRoot(), SimpleName.class).forEach(n -> taken.add(n.getIdentifier()));
    return taken;
  }

  /**
   * {@code name}, or it with a number after it, that {@code taken} does not hold and that is no
   * keyword: {@code int} becomes {@code int2}.
   */
  static String free(String name, Set<String> taken) {
    String out = name;
    for (int i = 2; taken.contains(out) || !SourceVersion.isName(out); i++) {
      out = name + i;
    }
    return out;
  }

  /**
   * A parameter's name from its type, for a method whose class file keeps none: {@code List} gives
   * {@code list}, {@code String[]} {@code strings}; a primitive, or a type whose name would be a
   * keyword ({@code Long}, {@code Class}), gives {@code value}.
   */
  static String parameterName(ITypeBinding type) {
    String simple = type.getErasure().getName().replace("[]", "s");
    String name = Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    return type.isPrimitive() || !SourceVersion.isName(name) ? "value" : name;
  }
}
