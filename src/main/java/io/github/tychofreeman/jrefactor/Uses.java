package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.GuardedPattern;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * inspect with uses: every use in the project of a type or member, found by binding, grouped by
 * what the use does with it (gorefactor's {@code inspect} {@code uses}).
 */
final class Uses {

  private Uses() {}

  /** What a use does with what it names, in the order shown. */
  private static final List<String> ROLES =
      List.of(
          "switch on it",
          "compared",
          "asserted",
          "a type switch case",
          "converted to",
          "made by new",
          "written",
          "passed as an interface",
          "called",
          "a method reference",
          "read");

  /** One use: its file, its line, and the code that shows what is done with it. */
  private record Use(String file, int line, String text) {}

  /** A declaration a name finds: its binding, and how the header describes it. */
  private record Found(String label, IBinding binding, String description) {}

  /** The uses of each of {@code names}, one block each, blank lines between. */
  static String of(SourceProject project, List<String> names) throws IOException {
    List<String> out = new ArrayList<>();
    for (String name : names) {
      String bare = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
      Holding holding = Holding.of(project, Names.simpleName(bare));
      List<Found> found;
      try {
        found = declarations(project, holding, name);
      } catch (Refusal r) {
        out.add(name + ": " + r.getMessage() + "\n");
        continue;
      }
      for (Found f : found) {
        out.add(usesOf(project, holding, f));
      }
    }
    return String.join("\n", out);
  }

  /** The declarations {@code name} names: a type, or each overload of a method, or a field. */
  private static List<Found> declarations(SourceProject project, Holding holding, String name) {
    String bare = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
    int dot = bare.lastIndexOf('.');
    List<AbstractTypeDeclaration> types = typesNamed(holding, bare);
    if (!types.isEmpty()) {
      AbstractTypeDeclaration t = one(project, types, name);
      ITypeBinding b = t.resolveBinding();
      return List.of(new Found(name, b, kind(b) + " " + b.getQualifiedName()));
    }
    if (dot < 0) {
      throw new Refusal("no declaration of that name in the project");
    }
    String member = bare.substring(dot + 1);
    List<AbstractTypeDeclaration> owners = typesNamed(holding, bare.substring(0, dot));
    if (owners.isEmpty()) {
      throw new Refusal("no declaration of that name in the project");
    }
    AbstractTypeDeclaration t = one(project, owners, name);
    String of = " of " + t.resolveBinding().getQualifiedName();
    List<Found> found = new ArrayList<>();
    List<MethodDeclaration> methods = new ArrayList<>();
    for (Object o : t.bodyDeclarations()) {
      if (o instanceof MethodDeclaration m
          && m.getName().getIdentifier().equals(member)
          && !m.isConstructor()) {
        methods.add(m);
      } else if (o instanceof FieldDeclaration f) {
        for (Object v : f.fragments()) {
          VariableDeclarationFragment fragment = (VariableDeclarationFragment) v;
          if (fragment.getName().getIdentifier().equals(member)) {
            IVariableBinding b = fragment.resolveBinding();
            found.add(new Found(name, b, "field" + of + ", " + b.getType().getName()));
          }
        }
      }
    }
    if (t instanceof EnumDeclaration en) {
      for (Object o : en.enumConstants()) {
        EnumConstantDeclaration c = (EnumConstantDeclaration) o;
        if (c.getName().getIdentifier().equals(member)) {
          found.add(new Found(name, c.resolveVariable(), "enum constant" + of));
        }
      }
    }
    for (MethodDeclaration m : methods) {
      String label = Declarations.methodLabel(m);
      if (name.contains("(") && !name.endsWith("." + label)) {
        continue;
      }
      IMethodBinding b = m.resolveBinding();
      found.add(
          new Found(
              methods.size() == 1 ? name : bare.substring(0, dot) + "." + label,
              b,
              "method" + of + ", " + signature(b)));
    }
    if (found.isEmpty()) {
      throw new Refusal("no declaration of that name in the project");
    }
    return found;
  }

  /**
   * The type declarations of {@code holding} that {@code label} names: Type, Outer.Inner, pkg.Type.
   */
  private static List<AbstractTypeDeclaration> typesNamed(Holding holding, String label) {
    List<AbstractTypeDeclaration> out = new ArrayList<>();
    for (CompilationUnit unit : holding.units().values()) {
      for (AbstractTypeDeclaration t : Nodes.all(unit, AbstractTypeDeclaration.class)) {
        ITypeBinding b = t.resolveBinding();
        String chain = Names.typeLabelAt(t);
        if (b != null
            && (chain.equals(label)
                || chain.endsWith("." + label)
                || b.getQualifiedName().equals(label))) {
          out.add(t);
        }
      }
    }
    return out;
  }

  /** The one of {@code types}, or a refusal naming where each is. */
  private static AbstractTypeDeclaration one(
      SourceProject project, List<AbstractTypeDeclaration> types, String name) {
    if (types.size() == 1) {
      return types.get(0);
    }
    List<String> places = new ArrayList<>();
    for (AbstractTypeDeclaration t : types) {
      places.add(
          project.relative(Jdt.fileOf((CompilationUnit) t.getRoot()))
              + " (package "
              + t.resolveBinding().getPackage().getName()
              + ")");
    }
    throw new Refusal(
        "declared in "
            + String.join(", ", places)
            + "; name it more fully (pkg.Type, Type.member)");
  }

  private static String kind(ITypeBinding b) {
    if (b.isAnnotation()) {
      return "annotation";
    }
    if (b.isEnum()) {
      return "enum";
    }
    if (b.isRecord()) {
      return "record";
    }
    return b.isInterface() ? "interface" : "class";
  }

  /** "int water(int)". */
  private static String signature(IMethodBinding b) {
    List<String> params = new ArrayList<>();
    for (ITypeBinding p : b.getParameterTypes()) {
      params.add(p.getName());
    }
    return b.getReturnType().getName() + " " + b.getName() + "(" + String.join(", ", params) + ")";
  }

  /** The block for {@code f}: its header, then its uses by role, each by file and line. */
  private static String usesOf(SourceProject project, Holding holding, Found f) {
    String key = key(f.binding());
    Map<String, List<Use>> byRole = new LinkedHashMap<>();
    int n = 0;
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      CompilationUnit unit = e.getValue();
      String text = holding.texts().get(e.getKey());
      String rel = project.relative(e.getKey()).toString();
      for (SimpleName name : Nodes.all(unit, SimpleName.class)) {
        IBinding b = name.resolveBinding();
        if (b == null || name.isDeclaration() || !key.equals(key(b)) || outsideCode(name)) {
          continue;
        }
        n++;
        String[] role = classify(text, name, b);
        byRole
            .computeIfAbsent(role[0], k -> new ArrayList<>())
            .add(new Use(rel, unit.getLineNumber(name.getStartPosition()), role[1]));
      }
    }
    StringBuilder out = new StringBuilder(f.label() + " (" + f.description() + "): ");
    if (n == 0) {
      return out.append("no uses\n").toString();
    }
    out.append(n).append(n == 1 ? " use\n" : " uses\n");
    for (String role : ROLES) {
      List<Use> uses = byRole.getOrDefault(role, List.of());
      if (uses.isEmpty()) {
        continue;
      }
      uses.sort(Comparator.comparing(Use::file).thenComparingInt(Use::line));
      out.append(role).append(" (").append(uses.size()).append("):\n");
      for (Use u : uses) {
        out.append("  ").append(u.file()).append(':').append(u.line()).append("  ");
        out.append(u.text()).append('\n');
      }
    }
    return out.toString();
  }

  /** The key a use and its declaration share: of the declaration, generic or not. */
  private static String key(IBinding b) {
    if (b instanceof ITypeBinding t) {
      return t.getTypeDeclaration().getKey();
    }
    if (b instanceof IMethodBinding m) {
      return m.getMethodDeclaration().getKey();
    }
    if (b instanceof IVariableBinding v) {
      return v.getVariableDeclaration().getKey();
    }
    return b.getKey();
  }

  /** Whether {@code n} is in a comment, an import or the package declaration: not a use in code. */
  private static boolean outsideCode(ASTNode n) {
    for (ASTNode p = n; p != null; p = p.getParent()) {
      if (p instanceof Javadoc
          || p instanceof ImportDeclaration
          || p instanceof PackageDeclaration) {
        return true;
      }
    }
    return false;
  }

  /** What the use {@code n} does with what it names, and the code that shows it. */
  private static String[] classify(String text, SimpleName n, IBinding b) {
    if (b instanceof ITypeBinding) {
      String[] typed = classifyType(n);
      if (typed != null) {
        return typed;
      }
    }
    ASTNode e = n;
    if (e.getParent() instanceof QualifiedName q && q.getName() == e
        || e.getParent() instanceof FieldAccess f && f.getName() == e) {
      e = e.getParent();
    }
    while (e.getParent() instanceof ParenthesizedExpression p) {
      e = p;
    }
    ASTNode parent = e.getParent();
    if (parent instanceof MethodInvocation mi && mi.getName() == n
        || parent instanceof SuperMethodInvocation si && si.getName() == n) {
      return new String[] {"called", Nodes.show(parent)};
    }
    if (parent instanceof MethodReference) {
      return new String[] {"a method reference", Nodes.show(parent)};
    }
    Expression switched =
        parent instanceof SwitchStatement s
            ? s.getExpression()
            : parent instanceof SwitchExpression s ? s.getExpression() : null;
    if (switched == e) {
      List<String> values = new ArrayList<>();
      List<?> statements =
          parent instanceof SwitchStatement s
              ? s.statements()
              : ((SwitchExpression) parent).statements();
      for (Object o : statements) {
        if (o instanceof SwitchCase c) {
          if (c.isDefault()) {
            values.add("default");
          }
          for (Object v : c.expressions()) {
            values.add(Nodes.show((ASTNode) v));
          }
        }
      }
      return new String[] {
        "switch on it", "switch (" + Nodes.show(e) + "): " + String.join(", ", values)
      };
    }
    if (parent instanceof InfixExpression i
        && (i.getOperator() == InfixExpression.Operator.EQUALS
            || i.getOperator() == InfixExpression.Operator.NOT_EQUALS)) {
      return new String[] {"compared", Nodes.show(parent)};
    }
    if (parent instanceof MethodInvocation mi
        && mi.getName().getIdentifier().equals("equals")
        && mi.arguments().size() == 1
        && (mi.getExpression() == e || mi.arguments().get(0) == e)) {
      return new String[] {"compared", Nodes.show(parent)};
    }
    if (parent instanceof InstanceofExpression i && i.getLeftOperand() == e
        || parent instanceof PatternInstanceofExpression p && p.getLeftOperand() == e
        || parent instanceof CastExpression c && c.getExpression() == e) {
      return new String[] {"asserted", Nodes.show(parent)};
    }
    if (parent instanceof Assignment a && a.getLeftHandSide() == e
        || parent instanceof PostfixExpression
        || parent instanceof PrefixExpression p
            && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                || p.getOperator() == PrefixExpression.Operator.DECREMENT)) {
      return new String[] {"written", Nodes.show(parent)};
    }
    ITypeBinding parameter = parameterType(parent, e);
    if (parameter != null && parameter.isInterface()) {
      return new String[] {"passed as an interface", Nodes.show(parent)};
    }
    for (ASTNode p = e; p != null; p = p.getParent()) {
      if (p instanceof Statement || p instanceof BodyDeclaration) {
        return new String[] {"read", firstLine(text, p)};
      }
    }
    return new String[] {"read", Nodes.show(e)};
  }

  /**
   * What the use {@code n} of a type does with it where that is particular to types: a case of a
   * switch on types, a cast to it, a {@code new}, an {@code instanceof}; null for the other uses.
   */
  private static String[] classifyType(SimpleName n) {
    ASTNode t = n;
    while (t.getParent() instanceof SimpleType
        || t.getParent() instanceof QualifiedType
        || t.getParent() instanceof NameQualifiedType
        || t.getParent() instanceof QualifiedName q && q.getName() == t
        || t.getParent() instanceof ParameterizedType p && p.getType() == t) {
      t = t.getParent();
    }
    ASTNode parent = t.getParent();
    if (parent instanceof CastExpression c && c.getType() == t) {
      return new String[] {"converted to", Nodes.show(parent)};
    }
    if (parent instanceof ClassInstanceCreation c && c.getType() == t) {
      return new String[] {"made by new", Nodes.show(parent)};
    }
    if (parent instanceof InstanceofExpression i && i.getRightOperand() == t) {
      return new String[] {"asserted", Nodes.show(parent)};
    }
    if (parent instanceof SingleVariableDeclaration v
        && v.getType() == t
        && v.getParent() instanceof TypePattern pattern) {
      ASTNode holder = pattern.getParent();
      if (holder instanceof GuardedPattern g) {
        holder = g.getParent();
      }
      if (holder instanceof PatternInstanceofExpression) {
        return new String[] {"asserted", Nodes.show(holder)};
      }
      if (holder instanceof SwitchCase c) {
        Expression switched =
            c.getParent() instanceof SwitchStatement s
                ? s.getExpression()
                : c.getParent() instanceof SwitchExpression s ? s.getExpression() : null;
        return new String[] {
          "a type switch case",
          "switch ("
              + (switched == null ? "?" : Nodes.show(switched))
              + "): case "
              + Nodes.show(pattern)
        };
      }
    }
    return null;
  }

  /** The declared type of the parameter {@code arg} is passed for in {@code call}, or null. */
  private static ITypeBinding parameterType(ASTNode call, ASTNode arg) {
    List<?> args;
    IMethodBinding m;
    if (call instanceof MethodInvocation mi) {
      args = mi.arguments();
      m = mi.resolveMethodBinding();
    } else if (call instanceof SuperMethodInvocation si) {
      args = si.arguments();
      m = si.resolveMethodBinding();
    } else if (call instanceof ClassInstanceCreation c) {
      args = c.arguments();
      m = c.resolveConstructorBinding();
    } else if (call instanceof ConstructorInvocation c) {
      args = c.arguments();
      m = c.resolveConstructorBinding();
    } else {
      return null;
    }
    int i = args.indexOf(arg);
    if (i < 0 || m == null || m.getParameterTypes().length == 0) {
      return null;
    }
    ITypeBinding[] params = m.getParameterTypes();
    if (m.isVarargs() && i >= params.length - 1) {
      ITypeBinding last = params[params.length - 1];
      return args.size() == params.length
              && arg instanceof Expression x
              && x.resolveTypeBinding() != null
              && x.resolveTypeBinding().isArray()
          ? last
          : last.getComponentType();
    }
    return i < params.length ? params[i] : null;
  }

  /** {@code node}'s first line in {@code text}, after its Javadoc, without a closing {@code {}. */
  private static String firstLine(String text, ASTNode node) {
    int start = node.getStartPosition();
    if (node instanceof BodyDeclaration d && d.getJavadoc() != null) {
      start = Nodes.end(d.getJavadoc());
    }
    String s = text.substring(start, Nodes.end(node)).strip();
    int nl = s.indexOf('\n');
    String line = (nl < 0 ? s : s.substring(0, nl)).strip();
    if (line.endsWith("{")) {
      line = line.substring(0, line.length() - 1).strip();
    }
    return line;
  }
}
