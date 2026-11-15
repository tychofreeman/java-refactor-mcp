package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.TextBlock;

/**
 * gorefactor's parameterize: methods of one type that are the same but for constants become calls
 * of one new method, the first one's body with those constants as parameters. Each keeps its
 * signature and its callers.
 */
final class Parameterize {

  private Parameterize() {}

  static Outcome run(
      SourceProject project,
      String file,
      List<String> functions,
      String name,
      List<String> parameters)
      throws Exception {
    if (functions.size() < 2) {
      throw new Refusal("Give two or more methods in functions.");
    }
    if (!SourceVersion.isName(name)) {
      throw new Refusal("name " + name + " is not a Java identifier.");
    }
    Opened opened = Opened.of(project, file);
    List<MethodDeclaration> methods = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    for (String f : functions) {
      Declarations.Named named = Declarations.find(opened.unit(), f, opened.rel());
      if (!(named.decl() instanceof MethodDeclaration m) || m.isConstructor()) {
        throw new Refusal(f + " is not a method of " + opened.rel() + ".");
      }
      if (m.getBody() == null || m.resolveBinding() == null) {
        throw new Refusal(f + " has no body that resolves.");
      }
      if (!m.typeParameters().isEmpty()) {
        throw new Refusal(f + " is generic; parameterize takes methods without type parameters.");
      }
      methods.add(m);
      labels.add(label(m));
    }
    MethodDeclaration first = methods.get(0);
    for (int i = 1; i < methods.size(); i++) {
      sameShape(first, methods.get(i), labels.get(0), labels.get(i));
    }

    // The literals of the first method that differ in some other, and each method's literal there.
    List<Expression> differ = new ArrayList<>();
    List<Map<Expression, Expression>> found = new ArrayList<>();
    found.add(null);
    for (int i = 1; i < methods.size(); i++) {
      MethodDeclaration other = methods.get(i);
      Literals matcher = new Literals(first, other);
      if (!first.getBody().subtreeMatch(matcher, other.getBody())) {
        String[] at = miss(first.getBody(), other.getBody(), first, other, opened.text());
        throw new Refusal(
            labels.get(0)
                + " and "
                + labels.get(i)
                + " differ at "
                + at[0]
                + " and "
                + at[1]
                + ", which are not constants; parameterize makes a parameter only of constants.");
      }
      found.add(matcher.found);
      matcher.found.forEach((mine, theirs) -> {
        if (!source(opened.text(), mine).equals(source(opened.text(), theirs))
            && !differ.contains(mine)) {
          differ.add(mine);
        }
      });
    }
    differ.sort(Comparator.comparingInt(ASTNode::getStartPosition));
    if (differ.isEmpty()) {
      throw new Refusal(
          Names.listed(labels) + " do not differ; fold the one to keep, then inline the other.");
    }
    if (differ.size() != parameters.size()) {
      throw new Refusal(
          "the methods differ in "
              + Names.plural(differ.size(), "constant")
              + " ("
              + String.join(", ", differ.stream().map(e -> source(opened.text(), e)).toList())
              + " in "
              + labels.get(0)
              + "), so give "
              + Names.plural(differ.size(), "name")
              + " in parameters, not "
              + parameters.size()
              + ".");
    }
    checkNames(first, labels.get(0), name, parameters);

    String text = opened.text();
    List<String> added = new ArrayList<>();
    for (int k = 0; k < differ.size(); k++) {
      added.add(typeName(differ.get(k).resolveTypeBinding()) + " " + parameters.get(k));
    }
    List<TextEdit> edits = new ArrayList<>();
    for (int i = 0; i < methods.size(); i++) {
      MethodDeclaration m = methods.get(i);
      List<String> args = new ArrayList<>();
      for (Object p : m.parameters()) {
        args.add(((SingleVariableDeclaration) p).getName().getIdentifier());
      }
      for (Expression e : differ) {
        args.add(source(text, i == 0 ? e : found.get(i).get(e)));
      }
      edits.add(callIn(text, m, name + "(" + String.join(", ", args) + ");"));
    }
    AbstractTypeDeclaration type = (AbstractTypeDeclaration) first.getParent();
    edits.addAll(
        MemberMove.addTo(
            text,
            type,
            generalized(text, first, name, differ, parameters, added),
            Placement.Rank.OTHER));
    String after = TextEdit.apply(text, edits, 0);
    String made = Names.typeLabel(type.resolveBinding()) + "." + name;
    Edits.Written written =
        Edits.gated(
            project,
            opened.file(),
            text,
            after,
            List.of(),
            "making " + Names.listed(labels) + " calls of a new " + made,
            "A constant that must stay one (a case label, an annotation's value) cannot be a"
                + " parameter: leave that method out, or make the methods alike there first.");
    return new Outcome(
        "made "
            + Names.listed(labels)
            + " calls of a new "
            + made
            + "("
            + String.join(", ", allParameters(first, added))
            + ") in "
            + opened.rel()
            + written.fixed().said(),
        written.declared());
  }

  /** {@code m} as a result names it: Type.method. */
  private static String label(MethodDeclaration m) {
    return Names.typeLabel(m.resolveBinding().getDeclaringClass()) + "." + m.getName();
  }

  /** Refuses {@code b} unless it is a method of {@code a}'s type with {@code a}'s signature. */
  private static void sameShape(MethodDeclaration a, MethodDeclaration b, String la, String lb) {
    IMethodBinding x = a.resolveBinding();
    IMethodBinding y = b.resolveBinding();
    if (!x.getDeclaringClass().isEqualTo(y.getDeclaringClass())) {
      throw new Refusal(la + " and " + lb + " are not methods of one type.");
    }
    if (Modifier.isStatic(x.getModifiers()) != Modifier.isStatic(y.getModifiers())) {
      throw new Refusal(la + " and " + lb + " are not both static or both instance methods.");
    }
    boolean same =
        x.getReturnType().isEqualTo(y.getReturnType())
            && x.getParameterTypes().length == y.getParameterTypes().length;
    for (int i = 0; same && i < x.getParameterTypes().length; i++) {
      same = x.getParameterTypes()[i].isEqualTo(y.getParameterTypes()[i]);
    }
    if (!same) {
      throw new Refusal(
          la
              + " and "
              + lb
              + " have different signatures: "
              + Names.methodLabel(x)
              + " returns "
              + x.getReturnType().getName()
              + ", "
              + Names.methodLabel(y)
              + " returns "
              + y.getReturnType().getName()
              + ".");
    }
  }

  /**
   * Refuses a new method name the type or a supertype has, and parameter names that are not
   * identifiers, are given twice, or that the first method already uses.
   */
  private static void checkNames(
      MethodDeclaration first, String label, String name, List<String> parameters) {
    for (ITypeBinding t : Hierarchy.supertypes(first.resolveBinding().getDeclaringClass(), true)) {
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (m.getName().equals(name)) {
          throw new Refusal(
              Names.typeLabel(t) + " already has a method " + name + "; choose another name.");
        }
      }
    }
    Set<String> used = new HashSet<>();
    for (SimpleName n : Nodes.all(first, SimpleName.class)) {
      if (n.resolveBinding() == null || n.resolveBinding() instanceof IVariableBinding) {
        used.add(n.getIdentifier());
      }
    }
    Set<String> given = new HashSet<>();
    for (String p : parameters) {
      if (!SourceVersion.isName(p)) {
        throw new Refusal("parameter " + p + " is not a Java identifier.");
      }
      if (!given.add(p)) {
        throw new Refusal("parameter " + p + " is named twice; give each constant its own name.");
      }
      if (used.contains(p)) {
        throw new Refusal(
            label + " already uses the name " + p + "; choose another for the parameter.");
      }
    }
  }

  /**
   * The edit that makes {@code m}'s body the one statement {@code call}, returned when it returns.
   */
  private static TextEdit callIn(String text, MethodDeclaration m, String call) {
    int open = m.getBody().getStartPosition();
    int close = Nodes.end(m.getBody()) - 1;
    String statement =
        (m.getReturnType2() != null && !m.getReturnType2().toString().equals("void")
                ? "return "
                : "")
            + call;
    if (Lines.number(text, open) == Lines.number(text, close)) {
      return new TextEdit(open + 1, close, " " + statement + " ");
    }
    String indent = Lines.indentOf(text, close);
    return new TextEdit(
        open + 1,
        close,
        "\n" + indent + " ".repeat(Indent.step(text, 2)) + statement + "\n" + indent);
  }

  /**
   * The new method, as written in the file after the first line: private, static when the first
   * method is, the first method's return type, parameters, throws clause and body, its constants
   * {@code differ} made the parameters {@code names}.
   */
  private static String generalized(
      String text,
      MethodDeclaration first,
      String name,
      List<Expression> differ,
      List<String> names,
      List<String> added) {
    int start =
        first.getReturnType2() != null
            ? first.getReturnType2().getStartPosition()
            : first.getName().getStartPosition();
    int end = Nodes.end(first);
    List<TextEdit> edits = new ArrayList<>();
    edits.add(
        new TextEdit(
            first.getName().getStartPosition() - start, Nodes.end(first.getName()) - start, name));
    int close =
        text.indexOf(
            ')',
            first.parameters().isEmpty()
                ? Nodes.end(first.getName())
                : Nodes.end((ASTNode) first.parameters().get(first.parameters().size() - 1)));
    edits.add(
        new TextEdit(
            close - start,
            close - start,
            (first.parameters().isEmpty() ? "" : ", ") + String.join(", ", added)));
    for (int k = 0; k < differ.size(); k++) {
      Expression e = differ.get(k);
      edits.add(new TextEdit(e.getStartPosition() - start, Nodes.end(e) - start, names.get(k)));
    }
    String modifiers = Modifier.isStatic(first.getModifiers()) ? "private static " : "private ";
    return modifiers + TextEdit.apply(text.substring(start, end), edits, 0);
  }

  /** The new method's parameters: the first method's, then the constants'. */
  private static List<String> allParameters(MethodDeclaration first, List<String> added) {
    List<String> out = new ArrayList<>();
    for (Object p : first.parameters()) {
      SingleVariableDeclaration d = (SingleVariableDeclaration) p;
      out.add(d.getType() + (d.isVarargs() ? "..." : "") + " " + d.getName());
    }
    out.addAll(added);
    return out;
  }

  /** A constant's type as a parameter declares it. */
  private static String typeName(ITypeBinding type) {
    return type.isPrimitive() || type.getQualifiedName().equals("java.lang.String")
        ? type.getName()
        : type.getQualifiedName();
  }

  private static String source(String text, ASTNode node) {
    return text.substring(node.getStartPosition(), Nodes.end(node));
  }

  /**
   * Where two bodies stop matching: the innermost pair of nodes that do not match, past names to
   * the expressions around them, each as its text, or a statement by its kind and line.
   */
  private static String[] miss(
      ASTNode a, ASTNode b, MethodDeclaration first, MethodDeclaration other, String text) {
    descend:
    while (a.getNodeType() == b.getNodeType()) {
      for (Object o : a.structuralPropertiesForType()) {
        StructuralPropertyDescriptor d = (StructuralPropertyDescriptor) o;
        Object x = a.getStructuralProperty(d);
        Object y = b.getStructuralProperty(d);
        if (x instanceof ASTNode cx
            && y instanceof ASTNode cy
            && !cx.subtreeMatch(new Literals(first, other), cy)) {
          a = cx;
          b = cy;
          continue descend;
        }
        if (x instanceof List<?> lx && y instanceof List<?> ly && lx.size() == ly.size()) {
          for (int i = 0; i < lx.size(); i++) {
            ASTNode cx = (ASTNode) lx.get(i);
            ASTNode cy = (ASTNode) ly.get(i);
            if (!cx.subtreeMatch(new Literals(first, other), cy)) {
              a = cx;
              b = cy;
              continue descend;
            }
          }
        }
      }
      break;
    }
    while (a instanceof Name && b instanceof Name && named(a.getParent()) && named(b.getParent())) {
      a = a.getParent();
      b = b.getParent();
    }
    if (a instanceof Expression && b instanceof Expression) {
      return new String[] {Nodes.show(a), Nodes.show(b)};
    }
    return new String[] {
      a.getClass().getSimpleName() + " (line " + Lines.number(text, a.getStartPosition()) + ")",
      "line " + Lines.number(text, b.getStartPosition())
    };
  }

  /**
   * Matches one body against another that is the same but for literals of the same type, each pair
   * of which it records, the methods' parameters by position, and locals by name.
   */
  private static final class Literals extends ASTMatcher {
    final Map<Expression, Expression> found = new LinkedHashMap<>();
    private final List<String> mine = new ArrayList<>();
    private final List<String> theirs = new ArrayList<>();

    Literals(MethodDeclaration first, MethodDeclaration other) {
      for (IVariableBinding p : parameters(first)) {
        mine.add(p.getKey());
      }
      for (IVariableBinding p : parameters(other)) {
        theirs.add(p.getKey());
      }
    }

    private static List<IVariableBinding> parameters(MethodDeclaration m) {
      List<IVariableBinding> out = new ArrayList<>();
      for (Object p : m.parameters()) {
        out.add(((SingleVariableDeclaration) p).resolveBinding());
      }
      return out;
    }

    @Override
    public boolean match(StringLiteral node, Object other) {
      return literal(node, other);
    }

    @Override
    public boolean match(NumberLiteral node, Object other) {
      return literal(node, other);
    }

    @Override
    public boolean match(CharacterLiteral node, Object other) {
      return literal(node, other);
    }

    @Override
    public boolean match(TextBlock node, Object other) {
      return literal(node, other);
    }

    private boolean literal(Expression node, Object other) {
      if (!(other instanceof Expression x) || node.getNodeType() != x.getNodeType()) {
        return false;
      }
      ITypeBinding a = node.resolveTypeBinding();
      ITypeBinding b = x.resolveTypeBinding();
      if (a == null || b == null || !a.isEqualTo(b)) {
        return false;
      }
      found.put(node, x);
      return true;
    }

    @Override
    public boolean match(SimpleName node, Object other) {
      if (!(other instanceof SimpleName x)) {
        return false;
      }
      IBinding a = node.resolveBinding();
      IBinding b = x.resolveBinding();
      int i = a == null ? -1 : mine.indexOf(a.getKey());
      int j = b == null ? -1 : theirs.indexOf(b.getKey());
      if (i >= 0 || j >= 0) {
        return i == j;
      }
      if (!node.getIdentifier().equals(x.getIdentifier())) {
        return false;
      }
      if (a instanceof IVariableBinding va
          && !va.isField()
          && b instanceof IVariableBinding vb
          && !vb.isField()) {
        return true;
      }
      return Objects.equals(Bindings.declarationKey(a), Bindings.declarationKey(b));
    }
  }

  /**
   * Whether {@code n} is an expression a name is part of: {@code a.b}, {@code e.f}, {@code e.m()}.
   */
  private static boolean named(ASTNode n) {
    return n instanceof Name
        || n instanceof FieldAccess
        || n instanceof SuperFieldAccess
        || n instanceof MethodInvocation
        || n instanceof SuperMethodInvocation;
  }
}
