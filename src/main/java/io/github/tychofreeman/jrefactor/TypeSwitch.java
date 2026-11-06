package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.YieldStatement;

final class TypeSwitch {
  private TypeSwitch() {}

  /** One case: the type it matches, its pattern variable, and the expression it gives. */
  private record Case(ITypeBinding type, String variable, Expression value) {}

  /**
   * type_switch_to_method: the switch expression on {@code line}, whose cases are type patterns
   * over a project interface or abstract class, becomes a call of the method {@code name}, which
   * the supertype declares and each case's type implements with the case's expression.
   */
  static Outcome run(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    Integer line = args.integer("line");
    Precondition.require(
        Precondition.that(
            () -> line != null, () -> "type_switch_to_method needs line, where the switch starts."),
        Precondition.that(
            () -> SourceVersion.isIdentifier(name) && !SourceVersion.isKeyword(name),
            () -> name + " is not a valid method name."));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    SwitchExpression sw =
        opened.startingOn(
            line, SwitchExpression.class, x -> true, "switch expression", "", "the switch");
    ITypeBinding s = sw.getExpression().resolveTypeBinding();
    ITypeBinding result = sw.resolveTypeBinding();
    Path sFile = s == null ? null : fileOf(project, s);
    Precondition.require(
        Precondition.that(
            () ->
                s != null
                    && (s.isInterface() || Modifier.isAbstract(s.getModifiers()))
                    && sFile != null,
            () ->
                "the switch is over "
                    + (s == null ? "a value that does not resolve" : s.getName())
                    + ", which is not an interface or abstract class of the project to declare "
                    + name
                    + " on."),
        Precondition.that(() -> result != null, () -> "the switch's type does not resolve."));
    List<Case> cases = cases(sw, project);

    Map<Path, String> texts = new LinkedHashMap<>();
    texts.put(opened.file(), text);
    texts.putIfAbsent(sFile, Files.readString(sFile));
    for (Case c : cases) {
      Path f = fileOf(project, c.type());
      texts.putIfAbsent(f, Files.readString(f));
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    String r = Extract.typeName(result);
    AbstractTypeDeclaration sDecl = declaration(parsed.get(sFile), s);
    String abstractMethod = (s.isInterface() ? "" : "public abstract ") + r + " " + name + "();";
    edits
        .computeIfAbsent(sFile, k -> new ArrayList<>())
        .addAll(MemberMove.appendTo(texts.get(sFile), sDecl, abstractMethod));
    for (Case c : cases) {
      Path f = fileOf(project, c.type());
      AbstractTypeDeclaration t = declaration(parsed.get(f), c.type());
      for (IMethodBinding m : c.type().getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
          throw new Refusal(c.type().getName() + " already declares " + name + "().");
        }
      }
      String body = asReceiver(text, c);
      String method =
          "@Override\n  public " + r + " " + name + "() {\n    return " + body + ";\n  }";
      edits
          .computeIfAbsent(f, k -> new ArrayList<>())
          .addAll(MemberMove.appendTo(texts.get(f), t, method));
    }
    String selector =
        text.substring(sw.getExpression().getStartPosition(), Nodes.end(sw.getExpression()));
    boolean plain = sw.getExpression() instanceof Name;
    edits
        .computeIfAbsent(opened.file(), k -> new ArrayList<>())
        .add(
            new TextEdit(
                sw.getStartPosition(),
                Nodes.end(sw),
                (plain ? selector : "(" + selector + ")") + "." + name + "()"));
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "turning the switch into " + name + "()",
            "Check that each case's expression compiles in its type.");
    List<String> types = new ArrayList<>();
    for (Case c : cases) {
      types.add(c.type().getName());
    }
    return new Outcome(
        "declared "
            + r
            + " "
            + name
            + "() on "
            + s.getName()
            + ", implemented in "
            + String.join(", ", types)
            + "; the switch at line "
            + line
            + " of "
            + rel
            + " is now "
            + selector
            + "."
            + name
            + "()",
        declared);
  }

  /** The cases of {@code sw}, each a type pattern giving an expression; refused otherwise. */
  private static List<Case> cases(SwitchExpression sw, SourceProject project) throws IOException {
    List<Case> out = new ArrayList<>();
    List<?> statements = sw.statements();
    for (int i = 0; i < statements.size(); i++) {
      if (!(statements.get(i) instanceof SwitchCase c)) {
        continue;
      }
      if (c.isDefault()) {
        throw new Refusal(
            "the switch has a default, which type_switch_to_method does not move yet.");
      }
      if (!c.isSwitchLabeledRule()
          || c.expressions().size() != 1
          || !(c.expressions().get(0) instanceof TypePattern p)
          || i + 1 >= statements.size()
          || !(statements.get(i + 1) instanceof YieldStatement y)
          || y.getExpression() == null) {
        throw new Refusal(
            "each case must be a type pattern giving an expression (case Circle c -> ...); the one"
                + " at "
                + c.toString().strip()
                + " is not.");
      }
      // getPatternVariable2, the node the parse resolved; getPatternVariable's has no bindings.
      String v = p.getPatternVariable2().getName().getIdentifier();
      IVariableBinding variable = p.getPatternVariable2().resolveBinding();
      ITypeBinding bound = variable == null ? null : variable.getType();
      ITypeBinding t = bound == null ? null : bound.getErasure();
      if (t == null || fileOf(project, t) == null) {
        throw new Refusal(
            "the case "
                + c.toString().strip()
                + " is over "
                + (t == null ? "a type that does not resolve" : t.getQualifiedName())
                + ", not a type of the project's.");
      }
      usesOnly(y.getExpression(), v, t.getName());
      out.add(new Case(t, v, y.getExpression()));
    }
    return out;
  }

  /**
   * Refuses an expression that uses anything but {@code v}, statics and constants: a local, a
   * parameter, {@code this} or a member of the method's own class, which on the case's type would
   * mean another thing.
   */
  private static void usesOnly(Expression e, String v, String typeName) {
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding b
          && !isVariable(n, v)
          && !Modifier.isStatic(b.getModifiers())
          && !qualifiedByOther(n)) {
        throw new Refusal(
            "the case of "
                + typeName
                + " uses "
                + n.getIdentifier()
                + ", which on "
                + typeName
                + " would not be there. Pass only what the pattern variable and constants give;"
                + " a value of the method needs to be a parameter of the new method, which"
                + " type_switch_to_method does not add yet.");
      }
    }
    for (MethodInvocation m : Nodes.all(e, MethodInvocation.class)) {
      IMethodBinding b = m.resolveMethodBinding();
      if (m.getExpression() == null && b != null && !Modifier.isStatic(b.getModifiers())) {
        throw new Refusal(
            "the case of "
                + typeName
                + " calls "
                + m.getName()
                + "() of the method's own class, which on "
                + typeName
                + " would mean another method.");
      }
    }
    if (!Nodes.all(e, ThisExpression.class).isEmpty()) {
      throw new Refusal("a case uses this, which on the case's type would mean another object.");
    }
  }

  /** Whether {@code n} is the member part of a name or access qualified by something else. */
  private static boolean qualifiedByOther(SimpleName n) {
    return (n.getParent() instanceof QualifiedName q && q.getName() == n)
        || (n.getParent() instanceof FieldAccess f && f.getName() == n);
  }

  /** Whether {@code n} is the case's pattern variable {@code v}: a local of that name. */
  private static boolean isVariable(SimpleName n, String v) {
    return n.getIdentifier().equals(v)
        && !qualifiedByOther(n)
        && n.resolveBinding() instanceof IVariableBinding b
        && !b.isField();
  }

  /**
   * The case's expression with its pattern variable as the receiver: {@code c.r()} becomes {@code
   * r()}, a bare {@code c} becomes {@code this}.
   */
  private static String asReceiver(String text, Case c) {
    Expression e = c.value();
    int base = e.getStartPosition();
    List<TextEdit> edits = new ArrayList<>();
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (!isVariable(n, c.variable())) {
        continue;
      }
      ASTNode p = n.getParent();
      boolean receiver =
          (p instanceof MethodInvocation m && m.getExpression() == n)
              || (p instanceof FieldAccess f && f.getExpression() == n)
              || (p instanceof QualifiedName q && q.getQualifier() == n);
      int s = n.getStartPosition() - base;
      edits.add(
          receiver
              ? new TextEdit(s, s + n.getLength() + 1, "")
              : new TextEdit(s, s + n.getLength(), "this"));
    }
    edits.sort((a, b) -> b.start() - a.start());
    return TextEdit.apply(text.substring(base, Nodes.end(e)), edits, 0);
  }

  /** The top-level declaration of {@code t} in {@code unit}. */
  private static AbstractTypeDeclaration declaration(CompilationUnit unit, ITypeBinding t) {
    for (Object o : unit.types()) {
      AbstractTypeDeclaration d = (AbstractTypeDeclaration) o;
      if (d.getName().getIdentifier().equals(t.getErasure().getName())) {
        return d;
      }
    }
    throw new Refusal(t.getName() + " is not a top-level type of its file.");
  }

  /** The project file that declares the top-level type {@code t}, or null. */
  private static Path fileOf(SourceProject project, ITypeBinding t) throws IOException {
    if (t.getDeclaringClass() != null) {
      return null;
    }
    String path = t.getErasure().getQualifiedName().replace('.', '/') + ".java";
    for (Path f : project.javaFiles()) {
      if (("/" + project.relative(f)).endsWith("/" + path)) {
        return f.toAbsolutePath().normalize();
      }
    }
    return null;
  }
}
