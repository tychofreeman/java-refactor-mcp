package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;

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

  /** The top-level type that holds {@code t}, or {@code t}'s erasure when it is top-level. */
  static ITypeBinding outermost(ITypeBinding t) {
    ITypeBinding top = t.getErasure();
    while (top.getDeclaringClass() != null) {
      top = top.getDeclaringClass();
    }
    return top;
  }

  /** The variable {@code e} names, as a name or {@code this.f}; null for any other expression. */
  static IVariableBinding variable(Expression e) {
    if (e instanceof SimpleName n && n.resolveBinding() instanceof IVariableBinding v) {
      return v;
    }
    if (e instanceof FieldAccess f
        && f.getExpression() instanceof ThisExpression t
        && t.getQualifier() == null) {
      return f.resolveFieldBinding();
    }
    return null;
  }

  /**
   * {@code n} and {@code what}, made plural unless n is 1: {@code 1 occurrence}, {@code 2
   * occurrences}.
   */
  static String plural(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  /**
   * "Outer.Cart.add(List<String>, int)": the method's class with its enclosing types, and {@link
   * #memberLabel}, which {@link Declarations#find} takes back as a name.
   */
  static String methodLabel(IMethodBinding b) {
    return typeLabel(b.getDeclaringClass()) + "." + memberLabel(b);
  }

  /**
   * "Outer.Inner": the names of the type declarations that hold {@code node}, outermost first, and
   * its own when it is one; through local and anonymous classes too.
   */
  static String typeLabelAt(ASTNode node) {
    String out = null;
    for (ASTNode p = node; p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t) {
        String name = t.getName().getIdentifier();
        out = out == null ? name : name + "." + out;
      }
    }
    return out;
  }

  /** The part of a qualified name after its last dot: {@code Map} for {@code java.util.Map}. */
  static String simpleName(String qualified) {
    return qualified.substring(qualified.lastIndexOf('.') + 1);
  }

  /** The package {@code u} declares, or the empty string for the default package. */
  static String packageOf(CompilationUnit u) {
    return u.getPackage() == null ? "" : u.getPackage().getName().getFullyQualifiedName();
  }

  /** "a", "a and b", "a, b and c". */
  static String listed(List<String> items) {
    int last = items.size() - 1;
    return last == 0
        ? items.get(0)
        : String.join(", ", items.subList(0, last)) + " and " + items.get(last);
  }

  /**
   * Whether {@code params} (as {@link Qualified#params}, without spaces) names {@code m}'s
   * parameters: all when null, else its types as {@link #memberLabel} writes them, written or
   * erased, or the same with qualifiers dropped.
   */
  static boolean takes(IMethodBinding m, List<String> params) {
    if (params == null) {
      return true;
    }
    IMethodBinding d = m.getMethodDeclaration();
    for (List<String> types : List.of(writtenTypes(d), erasedTypes(d), writtenTypes(m))) {
      List<String> plain = types.stream().map(t -> t.replace(" ", "")).toList();
      if (plain.equals(params)
          || plain.stream()
              .map(Names::unqualified)
              .toList()
              .equals(params.stream().map(Names::unqualified).toList())) {
        return true;
      }
    }
    return false;
  }

  /**
   * "add(List<String>, int)": the method's name and its parameter types as source writes them, or
   * erased ({@code raise(Throwable)}) when another method of its class would read alike, as {@link
   * Declarations#methodLabel} labels a declaration.
   */
  static String memberLabel(IMethodBinding b) {
    IMethodBinding m = b.getMethodDeclaration();
    List<String> written = writtenTypes(m);
    for (IMethodBinding other : m.getDeclaringClass().getDeclaredMethods()) {
      if (!other.getKey().equals(m.getKey())
          && other.getName().equals(m.getName())
          && writtenTypes(other).equals(written)) {
        return m.getName() + "(" + String.join(", ", erasedTypes(m)) + ")";
      }
    }
    return m.getName() + "(" + String.join(", ", written) + ")";
  }

  /** {@code m}'s parameter types by simple names, with type arguments: {@code Map.Entry<K, V>}. */
  private static List<String> writtenTypes(IMethodBinding m) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : m.getParameterTypes()) {
      types.add(typeText(Extract.typeName(t)));
    }
    return varargs(m, types);
  }

  /** {@code m}'s parameter types erased: a type variable's first bound, no type arguments. */
  private static List<String> erasedTypes(IMethodBinding m) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : m.getParameterTypes()) {
      ITypeBinding e = t.getErasure();
      ITypeBinding element = e.isArray() ? e.getElementType().getErasure() : e;
      String name = element.isPrimitive() ? element.getName() : typeLabel(element);
      types.add(name + "[]".repeat(e.isArray() ? e.getDimensions() : 0));
    }
    return varargs(m, types);
  }

  /** {@code types} with the last one's {@code []} written {@code ...} when {@code m} is varargs. */
  private static List<String> varargs(IMethodBinding m, List<String> types) {
    if (m.isVarargs() && !types.isEmpty()) {
      String last = types.get(types.size() - 1);
      types.set(types.size() - 1, last.substring(0, last.length() - 2) + "...");
    }
    return types;
  }

  /**
   * A parameter type as a label writes it, whatever its spacing: a space after each comma and
   * around {@code extends}, {@code super} and {@code &}, none elsewhere ({@code Map<K, ? extends
   * V>}).
   */
  static String typeText(String type) {
    return type.replace(" ", "")
        .replace(",", ", ")
        .replace("&", " & ")
        .replaceAll("\\?(extends|super)", "? $1 ");
  }

  /**
   * Qualifiers dropped from a type as written: {@code java.util.List<Map.Entry<K,V>>} as {@code
   * List<Entry<K,V>>}, for comparing a type written qualified with one that is not.
   */
  static String unqualified(String type) {
    return type.replaceAll("[A-Za-z_$][\\w$]*\\.(?=[A-Za-z_$])", "");
  }
}
