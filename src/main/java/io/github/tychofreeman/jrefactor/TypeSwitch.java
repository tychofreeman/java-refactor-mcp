package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.YieldStatement;

final class TypeSwitch {
  private TypeSwitch() {}

  /**
   * One case: the type it matches (null for a default or final else, which {@code S} itself
   * implements), its pattern variable (or the selector's name for a default), and its body: the
   * expression it gives, or its statements.
   */
  record Case(ITypeBinding type, String variable, ASTNode body) {}

  /**
   * type_switch_to_method: the switch (expression or statement) or instanceof chain on {@code
   * line}, over a project interface or abstract class, becomes a call of the method {@code name},
   * which each case's type implements with the case's body, and the supertype declares (or, with a
   * default, implements).
   */
  static Outcome run(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String name = args.required("name");
    Integer line = args.integer("line");
    Precondition.require(Precondition.identifier(name, "the new method"));
    Opened opened = Opened.of(project, rel);
    String text = opened.text();
    ASTNode node =
        opened.startingOn(
            line,
            ASTNode.class,
            x ->
                x instanceof SwitchExpression
                    || x instanceof SwitchStatement
                    || (x instanceof IfStatement i
                        && i.getExpression() instanceof PatternInstanceofExpression
                        && !(i.getParent() instanceof IfStatement p && p.getElseStatement() == i)),
            "switch, or if of instanceof patterns,",
            "",
            "the switch or the if");
    Expression over =
        node instanceof SwitchExpression sw
            ? sw.getExpression()
            : node instanceof SwitchStatement ss ? ss.getExpression() : null;
    if (over != null && over.resolveTypeBinding() instanceof ITypeBinding e && e.isEnum()) {
      return EnumSwitch.run(project, opened, node, over, e, name, line, args.string("as"));
    }
    // The selector's type first: over an int or a String no case could be read as a type.
    Expression selector =
        over != null
            ? over
            : ((PatternInstanceofExpression) ((IfStatement) node).getExpression()).getLeftOperand();
    ITypeBinding s = selector.resolveTypeBinding();
    Path sFile = s == null ? null : project.fileOf(s);
    Precondition.require(
        Precondition.that(
            () ->
                s != null
                    && (s.isInterface() || Modifier.isAbstract(s.getModifiers()))
                    && sFile != null,
            () ->
                "the switch is over "
                    + (s == null ? "a value that does not resolve" : s.getName())
                    + ", which is not an enum, interface or abstract class of the project, so there"
                    + " is no type to declare "
                    + name
                    + "() on. Leave the switch as it is, or extract_function it to name it as a"
                    + " method here."));
    ITypeBinding result = null;
    List<Case> cases;
    if (node instanceof SwitchExpression sw) {
      result = sw.resolveTypeBinding();
      cases = switchCases(sw.statements(), selector, project, true);
    } else if (node instanceof SwitchStatement ss) {
      cases = switchCases(ss.statements(), selector, project, false);
    } else {
      cases = chain((IfStatement) node, text, selector, project);
    }
    ITypeBinding r0 = result;
    Precondition.require(
        Precondition.that(
            () -> !(node instanceof SwitchExpression) || r0 != null,
            () -> "the switch's type does not resolve."));
    String r = result == null ? "void" : Extract.typeName(result);

    Map<Path, String> texts = new LinkedHashMap<>();
    texts.put(opened.file(), text);
    texts.putIfAbsent(sFile, Files.readString(sFile));
    for (Case c : cases) {
      if (c.type() != null) {
        Path f = project.fileOf(c.type());
        texts.putIfAbsent(f, Files.readString(f));
      }
    }
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    Case fallback = cases.stream().filter(c -> c.type() == null).findFirst().orElse(null);
    String own =
        fallback == null
            ? (s.isInterface() ? "" : "public abstract ") + r + " " + name + "();"
            : (s.isInterface() ? "default " : "public ")
                + r
                + " "
                + name
                + "() {\n"
                + body(text, fallback, result != null, "    ")
                + "\n  }";
    edits
        .computeIfAbsent(sFile, k -> new ArrayList<>())
        .addAll(MemberMove.appendTo(texts.get(sFile), declaration(parsed.get(sFile), s), own));
    List<String> types = new ArrayList<>();
    for (Case c : cases) {
      if (c.type() == null) {
        continue;
      }
      for (IMethodBinding m : c.type().getDeclaredMethods()) {
        if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
          throw new Refusal(c.type().getName() + " already declares " + name + "().");
        }
      }
      Path f = project.fileOf(c.type());
      String method =
          "@Override\n  public "
              + r
              + " "
              + name
              + "() {\n"
              + body(text, c, result != null, "    ")
              + "\n  }";
      edits
          .computeIfAbsent(f, k -> new ArrayList<>())
          .addAll(MemberMove.appendTo(texts.get(f), declaration(parsed.get(f), c.type()), method));
      types.add(c.type().getName());
    }
    String sel = Nodes.source(text, selector);
    String call =
        (selector instanceof Name ? sel : "(" + sel + ")")
            + "."
            + name
            + "()"
            + (node instanceof SwitchExpression ? "" : ";");
    edits
        .computeIfAbsent(opened.file(), k -> new ArrayList<>())
        .add(new TextEdit(node.getStartPosition(), Nodes.end(node), call));
    NameCheck.require(
        project,
        texts,
        edits,
        NameCheck.meanings(cases.stream().map(Case::body).toList()),
        "moving the cases into their types");
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            project,
            texts,
            edits,
            List.of(),
            "turning the switch into " + name + "()",
            "Check that each case's body compiles in its type.");
    Stubs.Declared declared = written.declared();
    return new Outcome(
        (fallback == null ? "declared " : "implemented ")
            + r
            + " "
            + name
            + "() on "
            + s.getName()
            + (types.isEmpty() ? "" : ", overridden in " + String.join(", ", types))
            + "; the code at line "
            + line
            + " of "
            + rel
            + " is now "
            + call
            + written.said(),
        declared);
  }

  /**
   * The body of the method a case becomes, indented by {@code indent}: {@code return} its
   * expression (when the switch gives a value), else its statements, the pattern variable as the
   * receiver.
   */
  static String body(String text, Case c, boolean value, String indent) {
    String b = asReceiver(text, c);
    if (c.body() instanceof Expression) {
      b = (value ? "return " : "") + b + ";";
    } else if (c.body() instanceof Block) {
      // The lines between the braces, each keeping its indentation, so indent() finds the common
      // one.
      b =
          b.substring(b.indexOf('{') + 1, b.lastIndexOf('}'))
              .replaceFirst("^[ \\t]*\\n", "")
              .stripTrailing();
    }
    return Primitives.indent(b, indent);
  }

  /** The cases of a switch's {@code statements}: arrow cases, each a type pattern or default. */
  private static List<Case> switchCases(
      List<?> statements, Expression selector, SourceProject project, boolean value)
      throws IOException {
    List<Case> out = new ArrayList<>();
    for (int i = 0; i < statements.size(); i++) {
      if (!(statements.get(i) instanceof SwitchCase c)) {
        continue;
      }
      ASTNode body = armBody(statements, i, value, false);
      if (c.isDefault()) {
        String v = selector instanceof SimpleName n ? n.getIdentifier() : null;
        usesOnly(body, v, "the supertype", "the pattern variable");
        out.add(new Case(null, v, body));
        continue;
      }
      if (c.expressions().size() != 1 || !(c.expressions().get(0) instanceof TypePattern p)) {
        throw new Refusal(
            "each case must be a type pattern (case Circle c -> ...); the one at "
                + c.toString().strip()
                + " is not.");
      }
      out.add(typed(p, body, project, c.toString().strip()));
    }
    return out;
  }

  /** The cases of an if chain of {@code s instanceof T v} conditions, a final else the default. */
  private static List<Case> chain(
      IfStatement first, String text, Expression selector, SourceProject project)
      throws IOException {
    String sel = Nodes.source(text, selector);
    // Each if of the chain evaluates the selector again; the call that replaces them, once.
    if (first.getElseStatement() instanceof IfStatement && Names.variable(selector) == null) {
      throw new Refusal(
          "the chain evaluates "
              + sel
              + " once in each if, and the call that replaces it would evaluate it once. Put it in a"
              + " local before the if first (extract_variable), then call again.");
    }
    List<Case> out = new ArrayList<>();
    for (Statement at = first; at != null; ) {
      if (!(at instanceof IfStatement i)) {
        String v = selector instanceof SimpleName n ? n.getIdentifier() : null;
        usesOnly(at, v, "the supertype", "the pattern variable");
        out.add(new Case(null, v, at));
        break;
      }
      if (!(i.getExpression() instanceof PatternInstanceofExpression p)
          || !(p.getPattern() instanceof TypePattern tp)
          || !LoopToStream.sameVariable(text, p.getLeftOperand(), selector)) {
        throw new Refusal(
            "each if of the chain must test " + sel + " instanceof a type with a variable.");
      }
      Case c = typed(tp, i.getThenStatement(), project, "if (" + p + ")");
      // The first test that matches wins; an override would run a later case of a subtype instead.
      for (Case earlier : out) {
        if (c.type().isSubTypeCompatible(earlier.type())) {
          throw new Refusal(
              "the case if ("
                  + p
                  + ") never runs: "
                  + c.type().getName()
                  + " comes after the case of "
                  + earlier.type().getName()
                  + ", one of its supertypes, so the earlier case matches first; as an override on "
                  + c.type().getName()
                  + " it would run instead. Delete that case, then call again.");
        }
      }
      out.add(c);
      at = i.getElseStatement();
    }
    return out;
  }

  /** The case of the type pattern {@code p} with {@code body}, checked. */
  private static Case typed(TypePattern p, ASTNode body, SourceProject project, String label)
      throws IOException {
    // getPatternVariable2, the node the parse resolved; getPatternVariable's has no bindings.
    String v = p.getPatternVariable2().getName().getIdentifier();
    IVariableBinding variable = p.getPatternVariable2().resolveBinding();
    ITypeBinding bound = variable == null ? null : variable.getType();
    ITypeBinding t = bound == null ? null : bound.getErasure();
    if (t == null || project.fileOf(t) == null) {
      throw new Refusal(
          "the case "
              + label
              + " is over "
              + (t == null ? "a type that does not resolve" : t.getQualifiedName())
              + ", not a type of the project's.");
    }
    usesOnly(body, v, t.getName(), "the pattern variable");
    return new Case(t, v, body);
  }

  /**
   * Refuses a body that uses anything but {@code v} (the pattern variable, or null; the refusal
   * calls it {@code variable}), its own locals, statics and constants: a local, a parameter, {@code
   * this} or a member of the method's own class would mean another thing on the case's type; a
   * {@code return}, {@code break} or {@code continue} that leaves the body would leave the new
   * method instead.
   */
  static void usesOnly(ASTNode e, String v, String typeName, String variable) {
    // By binding: a field read named as a local of another scope in the body is no local.
    java.util.Set<String> own = new java.util.HashSet<>();
    for (VariableDeclaration d : Nodes.all(e, VariableDeclaration.class)) {
      if (d.resolveBinding() != null) {
        own.add(d.resolveBinding().getKey());
      }
    }
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding b
          && !(v != null && isVariable(n, v))
          && !own.contains(b.getKey())
          && !Modifier.isStatic(b.getModifiers())
          && !qualifiedByOther(n)) {
        throw new Refusal(
            "the case of "
                + typeName
                + " uses "
                + n.getIdentifier()
                + ", which on "
                + typeName
                + " would not be there. Pass only what "
                + variable
                + " and constants give;"
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
    if (!Nodes.all(e, SuperMethodInvocation.class).isEmpty()
        || !Nodes.all(e, SuperFieldAccess.class).isEmpty()
        || !Nodes.all(e, SuperMethodReference.class).isEmpty()) {
      throw new Refusal(
          "the case of "
              + typeName
              + " uses super, which on "
              + typeName
              + " would mean its own superclass. Keep the switch as it is.");
    }
    for (Statement s : Nodes.all(e, Statement.class)) {
      boolean jump =
          s instanceof ReturnStatement
              || s instanceof BreakStatement
              || s instanceof ContinueStatement
              || s instanceof YieldStatement;
      if (jump && Effects.exits(s, e) && !ownYield(s, e)) {
        throw new Refusal(
            "the case of "
                + typeName
                + " has "
                + s.toString().strip()
                + ", which in the new method would leave it instead of the code around the switch.");
      }
    }
  }

  /** Whether {@code n} is the member part of a name or access qualified by something else. */
  static boolean qualifiedByOther(SimpleName n) {
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
   * The case's body with its variable as the receiver: {@code c.r()} becomes {@code r()}, a bare
   * {@code c} becomes {@code this}.
   */
  private static String asReceiver(String text, Case c) {
    ASTNode e = c.body();
    int base = e.getStartPosition();
    List<TextEdit> edits = new ArrayList<>();
    java.util.Set<String> locals = new java.util.HashSet<>();
    for (VariableDeclaration d : Nodes.all(e, VariableDeclaration.class)) {
      locals.add(d.getName().getIdentifier());
    }
    for (SimpleName n : Nodes.all(e, SimpleName.class)) {
      if (c.variable() == null || !isVariable(n, c.variable())) {
        continue;
      }
      if (inClass(n, e)) {
        String t = n.resolveTypeBinding().getName();
        String local = Names.parameterName(n.resolveTypeBinding());
        throw new Refusal(
            "the case uses "
                + n.getIdentifier()
                + " inside a class it declares, where this would be that class's object, not "
                + t
                + "'s. Declare a local for it before that class ("
                + t
                + " "
                + local
                + " = "
                + n.getIdentifier()
                + ";) and use the local inside the class, then call again.");
      }
      ASTNode p = n.getParent();
      boolean receiver =
          (p instanceof MethodInvocation m && m.getExpression() == n)
              || (p instanceof FieldAccess f && f.getExpression() == n)
              || (p instanceof QualifiedName q && q.getQualifier() == n);
      int s = n.getStartPosition() - base;
      // c.x is this.x where the body declares a local x, which a bare x would mean.
      boolean field =
          (p instanceof FieldAccess f && locals.contains(f.getName().getIdentifier()))
              || (p instanceof QualifiedName q && locals.contains(q.getName().getIdentifier()));
      edits.add(
          receiver && !field
              ? new TextEdit(s, s + n.getLength() + 1, "")
              : new TextEdit(s, s + n.getLength(), "this"));
    }
    for (YieldStatement y : Nodes.all(e, YieldStatement.class)) {
      if (ownYield(y, e)) {
        int s = y.getStartPosition() - base;
        edits.add(new TextEdit(s, s + "yield".length(), "return"));
      }
    }
    return TextEdit.apply(text.substring(base, Nodes.end(e)), edits, 0);
  }

  /**
   * The declaration of {@code t}, a top-level or member type, in {@code unit}: found by its name
   * with its enclosing types, so a unit parsed without bindings serves too.
   */
  static AbstractTypeDeclaration declaration(CompilationUnit unit, ITypeBinding t) {
    String label = Names.typeLabel(t);
    for (AbstractTypeDeclaration d : Nodes.all(unit, AbstractTypeDeclaration.class)) {
      String named = d.getName().getIdentifier();
      ASTNode p = d.getParent();
      for (; p instanceof AbstractTypeDeclaration outer; p = p.getParent()) {
        named = outer.getName().getIdentifier() + "." + named;
      }
      if (p instanceof CompilationUnit && named.equals(label)) {
        return d;
      }
    }
    throw new Refusal(t.getName() + " is not a type of its file.");
  }

  /** The project file that declares the top-level type {@code t}, or null. */
  static Path fileOf(SourceProject project, ITypeBinding t) throws IOException {
    if (t.getDeclaringClass() != null) {
      return null;
    }
    return project.fileDeclaring(t.getErasure().getQualifiedName());
  }

  /**
   * Whether {@code s} is a yield of the switch expression whose case {@code body} is: in the new
   * method it returns the value instead.
   */
  private static boolean ownYield(Statement s, ASTNode body) {
    if (!(s instanceof YieldStatement) || !(body.getParent() instanceof SwitchExpression sw)) {
      return false;
    }
    ASTNode p = s.getParent();
    while (p != null && !(p instanceof SwitchExpression)) {
      p = p.getParent();
    }
    return p == sw;
  }

  /** Whether {@code n} is inside an anonymous or local class declared within {@code body}. */
  private static boolean inClass(ASTNode n, ASTNode body) {
    for (ASTNode p = n.getParent(); p != null && p != body; p = p.getParent()) {
      if (p instanceof AnonymousClassDeclaration || p instanceof AbstractTypeDeclaration) {
        return true;
      }
    }
    return false;
  }

  /**
   * The body of the arrow case {@code statements[i]}: a yield's expression or, in a switch
   * statement ({@code !value}), an expression statement's expression or a block; with {@code
   * blockOrThrow}, a block in a switch expression too, and a throw. Refuses a case that gives none
   * of these.
   */
  static ASTNode armBody(List<?> statements, int i, boolean value, boolean blockOrThrow) {
    SwitchCase c = (SwitchCase) statements.get(i);
    Object next = i + 1 < statements.size() ? statements.get(i + 1) : null;
    ASTNode body =
        next instanceof YieldStatement y && y.getExpression() != null
            ? y.getExpression()
            : !value && next instanceof ExpressionStatement e
                ? e.getExpression()
                : next instanceof Block b && (blockOrThrow || !value)
                    ? b
                    : blockOrThrow && next instanceof ThrowStatement t ? t : null;
    if (!c.isSwitchLabeledRule() || body == null) {
      throw new Refusal(
          "each case must be an arrow case giving "
              + (value ? "an expression" : "a statement or a block")
              + "; the one at "
              + c.toString().strip()
              + " is not.");
    }
    return body;
  }
}
