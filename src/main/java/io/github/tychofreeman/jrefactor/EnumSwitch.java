package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/**
 * type_switch_to_method over an enum of the project: each constant takes its case. When every case
 * of a switch expression gives a constant (or null), the enum gains a field of the switch's type,
 * which its constructor sets ({@link EnumField}), and the switch becomes a call of the field's
 * accessor; otherwise each constant a case names overrides a method with the case's body, and the
 * enum declares it (abstract, or with a default's body, or empty when a statement leaves constants
 * out). {@code as} ({@code field} or {@code method}) chooses.
 */
final class EnumSwitch {
  private EnumSwitch() {}

  /** One case: the constants it names (none for a default), and its expression or statements. */
  private record Arm(List<String> constants, ASTNode body) {
    String label() {
      return constants.isEmpty() ? "default" : String.join(", ", constants);
    }
  }

  /** The switch on its line and what it is over: the enum {@code e}, declared in {@code eFile}. */
  private record At(
      SourceProject project,
      Opened opened,
      ASTNode node,
      Expression selector,
      ITypeBinding e,
      Path eFile,
      String name,
      int line) {

    /** The call the switch becomes. */
    String call() {
      String text = opened.text();
      String sel = text.substring(selector.getStartPosition(), Nodes.end(selector));
      return (selector instanceof ThisExpression
              ? ""
              : (selector instanceof Name ? sel : "(" + sel + ")") + ".")
          + name
          + "()"
          + (node instanceof SwitchStatement ? ";" : "");
    }

    /** The edit that replaces the switch with {@link #call}. */
    TextEdit replaced() {
      return new TextEdit(node.getStartPosition(), Nodes.end(node), call());
    }

    /** The end of the result: where the switch was, and what it is now. */
    String now() {
      return "; the code at line " + line + " of " + opened.rel() + " is now " + call();
    }
  }

  static Outcome run(
      SourceProject project,
      Opened opened,
      ASTNode node,
      Expression selector,
      ITypeBinding e,
      String name,
      int line,
      String as)
      throws Exception {
    Path eFile = project.fileOf(e);
    if (eFile == null) {
      throw new Refusal(
          "the switch is over "
              + e.getName()
              + ", which is not an enum of the project's to declare "
              + name
              + " on.");
    }
    if (as != null && !as.equals("field") && !as.equals("method")) {
      throw new Refusal("as is field or method, not " + as + ".");
    }
    boolean value = node instanceof SwitchExpression;
    ITypeBinding result = node instanceof SwitchExpression sw ? sw.resolveTypeBinding() : null;
    if (value && result == null) {
      throw new Refusal("the switch's type does not resolve.");
    }
    List<?> statements =
        node instanceof SwitchExpression sw
            ? sw.statements()
            : ((SwitchStatement) node).statements();
    String v = selector instanceof SimpleName n ? n.getIdentifier() : null;
    List<Arm> arms = arms(statements, value, e, name, v);
    if ("field".equals(as) && !value) {
      throw new Refusal(
          "as field needs a switch expression, whose cases give each constant its value; a switch"
              + " statement becomes a method in each constant: leave out as.");
    }
    boolean field = value && !"method".equals(as);
    for (Arm a : arms) {
      if (field && !constant(a.body())) {
        if ("field".equals(as)) {
          throw new Refusal(
              "the case of "
                  + a.label()
                  + " gives "
                  + opened.text().substring(a.body().getStartPosition(), Nodes.end(a.body()))
                  + ", which is not a constant, so it cannot be a field's value. Leave out as, or"
                  + " pass as method, for a method in each constant.");
        }
        field = false;
      }
    }
    At at = new At(project, opened, node, selector, e, eFile, name, line);
    Map<Path, String> texts = new LinkedHashMap<>();
    texts.put(opened.file(), opened.text());
    texts.putIfAbsent(eFile, Files.readString(eFile));
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    EnumDeclaration target = declaration(parsed.get(eFile), e);
    return field
        ? field(at, texts, target, arms, Extract.typeName(result))
        : method(at, texts, target, arms, value ? Extract.typeName(result) : "void", v, value);
  }

  /** The enum gains a field set from each case's constant value. */
  private static Outcome field(
      At at, Map<Path, String> texts, EnumDeclaration target, List<Arm> arms, String type)
      throws Exception {
    String text = at.opened().text();
    Map<String, ASTNode> cases = new LinkedHashMap<>();
    ASTNode fallback = null;
    for (Arm a : arms) {
      if (a.constants().isEmpty()) {
        fallback = a.body();
      }
      for (String k : a.constants()) {
        cases.put(k, a.body());
      }
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (Object o : target.enumConstants()) {
      String k = ((EnumConstantDeclaration) o).getName().getIdentifier();
      ASTNode x = cases.getOrDefault(k, fallback);
      values.put(k, text.substring(x.getStartPosition(), Nodes.end(x)));
    }
    Path eFile = at.eFile();
    String eText = texts.get(eFile);
    EnumField.Field field = EnumField.field(target, at.name(), null, values, eText, type);
    NameCheck.require(
        at.project(),
        texts,
        Map.of(eFile, field.edits()),
        NameCheck.meanings(arms.stream().map(Arm::body).toList()),
        "moving the cases' values into " + at.e().getName());

    List<TextEdit> eEdits = new ArrayList<>(field.edits());
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    if (eFile.equals(at.opened().file())) {
      eEdits.add(at.replaced());
    } else {
      edits.put(at.opened().file(), List.of(at.replaced()));
    }
    String withValues = TextEdit.apply(eText, eEdits, 0);
    String out =
        Declarations.plan(
                withValues,
                at.project().relative(eFile).toString(),
                Names.typeLabel(at.e()),
                field.members())
            .text();
    edits.put(eFile, List.of(new TextEdit(0, eText.length(), out)));
    String fix =
        "Check that each case's value type-checks as " + type + " in " + at.e().getName() + ".";
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            at.project(),
            texts,
            edits,
            List.of(),
            "turning the switch into " + at.name() + "()",
            fix);
    Stubs.Declared declared = written.declared();
    int count = values.size();
    return new Outcome(
        "added the field "
            + type
            + " "
            + at.name()
            + " to "
            + at.e().getName()
            + ", read by "
            + at.name()
            + "(); "
            + count
            + (count == 1
                ? " constant passes its case's value"
                : " constants pass their case's value")
            + at.now()
            + written.said(),
        declared);
  }

  /**
   * Each constant a case names overrides the method {@code type name()} with the case's body, in
   * its own body, and the enum declares the method: with the default's body, else abstract when
   * every constant has a case, else empty.
   */
  private static Outcome method(
      At at,
      Map<Path, String> texts,
      EnumDeclaration target,
      List<Arm> arms,
      String type,
      String v,
      boolean value)
      throws Exception {
    String text = at.opened().text();
    String name = at.name();
    String simple = target.getName().getIdentifier();
    EnumField.checkName(target.resolveBinding(), simple, name, null);
    Map<String, Arm> cases = new LinkedHashMap<>();
    Arm fallback = null;
    List<String> copied = new ArrayList<>();
    for (Arm a : arms) {
      if (a.constants().isEmpty()) {
        fallback = a;
      }
      if (a.constants().size() > 1) {
        copied.add(a.label());
      }
      for (String k : a.constants()) {
        cases.put(k, a);
      }
    }
    Path eFile = at.eFile();
    String eText = texts.get(eFile);
    String step = " ".repeat(Declarations.step(eText) > 0 ? Declarations.step(eText) : 2);
    List<TextEdit> eEdits = new ArrayList<>();
    List<String> overridden = new ArrayList<>();
    boolean covered = true;
    for (Object o : target.enumConstants()) {
      EnumConstantDeclaration c = (EnumConstantDeclaration) o;
      String k = c.getName().getIdentifier();
      Arm a = cases.get(k);
      if (a == null) {
        covered = false;
        continue;
      }
      AnonymousClassDeclaration anon = c.getAnonymousClassDeclaration();
      if (anon != null) {
        for (Object d : anon.bodyDeclarations()) {
          if (d instanceof MethodDeclaration m
              && m.getName().getIdentifier().equals(name)
              && m.parameters().isEmpty()) {
            throw new Refusal(k + " already declares " + name + "().");
          }
        }
      }
      String in = Lines.indentOf(eText, c.getStartPosition());
      String method =
          in
              + step
              + "@Override\n"
              + in
              + step
              + "public "
              + type
              + " "
              + name
              + "() {\n"
              + TypeSwitch.body(
                  text, new TypeSwitch.Case(null, v, a.body()), value, in + step + step)
              + "\n"
              + in
              + step
              + "}";
      eEdits.add(inBody(eText, c, k, method, in));
      overridden.add(k);
    }
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    if (eFile.equals(at.opened().file())) {
      eEdits.add(at.replaced());
    } else {
      edits.put(at.opened().file(), List.of(at.replaced()));
    }
    NameCheck.require(
        at.project(),
        texts,
        Map.of(eFile, eEdits),
        NameCheck.meanings(arms.stream().map(Arm::body).toList()),
        "moving the cases into " + at.e().getName());
    String bodies = TextEdit.apply(eText, eEdits, 0);

    String own =
        fallback != null
            ? "public "
                + type
                + " "
                + name
                + "() {\n"
                + TypeSwitch.body(
                    text, new TypeSwitch.Case(null, v, fallback.body()), value, "    ")
                + "\n  }"
            : covered
                ? "public abstract " + type + " " + name + "();"
                : "public " + type + " " + name + "() {}";
    String rel = at.project().relative(eFile).toString();
    EnumDeclaration again = declaration(Edits.parses(bodies, rel), at.e());
    List<TextEdit> appended = MemberMove.appendTo(bodies, again, own);
    if (fallback != null) {
      NameCheck.require(
          at.project(),
          Map.of(eFile, bodies),
          Map.of(eFile, appended),
          NameCheck.meanings(List.of(fallback.body())),
          "moving the default case into " + at.e().getName());
    }
    String out = TextEdit.apply(bodies, appended, 0);
    edits.put(eFile, List.of(new TextEdit(0, eText.length(), out)));
    String fix = "Check that each case's body compiles in " + at.e().getName() + ".";
    Edits.WrittenFiles written =
        Edits.gatedFiles(
            at.project(), texts, edits, List.of(), "turning the switch into " + name + "()", fix);
    Stubs.Declared declared = written.declared();
    return new Outcome(
        (fallback == null ? "declared " : "implemented ")
            + type
            + " "
            + name
            + "() on "
            + at.e().getName()
            + (overridden.isEmpty() ? "" : ", overridden in " + String.join(", ", overridden))
            + copied.stream()
                .map(l -> "; the case of " + l + " is copied into each of them")
                .reduce("", String::concat)
            + at.now()
            + written.said(),
        declared);
  }

  /**
   * The edit that puts {@code method} in the body of the constant {@code c}, indented by {@code
   * in}: a body is written after a constant without one, and the method goes last in one it has.
   */
  private static TextEdit inBody(
      String text, EnumConstantDeclaration c, String k, String method, String in) {
    AnonymousClassDeclaration anon = c.getAnonymousClassDeclaration();
    if (anon == null) {
      int end = Nodes.end(c);
      return new TextEdit(end, end, " {\n" + method + "\n" + in + "}");
    }
    int close = Nodes.end(anon) - 1;
    int line = Lines.start(text, close);
    if (text.substring(line, close).isBlank()) {
      return new TextEdit(
          line, line, (anon.bodyDeclarations().isEmpty() ? "" : "\n") + method + "\n");
    }
    if (anon.bodyDeclarations().isEmpty()) {
      return new TextEdit(anon.getStartPosition() + 1, close, "\n" + method + "\n" + in);
    }
    throw new Refusal(
        k
            + "'s body ends on the line of its last member; put its closing brace on a line of its"
            + " own first.");
  }

  /**
   * The cases of a switch's {@code statements}, each an arrow case giving an expression (or, in a
   * statement, an expression statement), a block or a throw, checked to use only what the enum
   * sees.
   */
  private static List<Arm> arms(
      List<?> statements, boolean value, ITypeBinding e, String name, String v) {
    List<Arm> out = new ArrayList<>();
    for (int i = 0; i < statements.size(); i++) {
      if (!(statements.get(i) instanceof SwitchCase c)) {
        continue;
      }
      Object next = i + 1 < statements.size() ? statements.get(i + 1) : null;
      ASTNode body =
          next instanceof YieldStatement y && y.getExpression() != null
              ? y.getExpression()
              : !value && next instanceof ExpressionStatement s
                  ? s.getExpression()
                  : next instanceof Block || next instanceof ThrowStatement
                      ? (Statement) next
                      : null;
      if (!c.isSwitchLabeledRule() || body == null) {
        throw new Refusal(
            "each case must be an arrow case giving "
                + (value ? "an expression" : "a statement or a block")
                + "; the one at "
                + c.toString().strip()
                + " is not.");
      }
      Arm a = new Arm(constants(c, e, name), body);
      TypeSwitch.usesOnly(body, v, a.label(), "the switch's value");
      checkStatics(body, a.label(), e);
      out.add(a);
    }
    return out;
  }

  /** Whether {@code x} is a compile-time constant or {@code null}. */
  private static boolean constant(ASTNode x) {
    return x instanceof NullLiteral
        || (x instanceof Expression e && e.resolveConstantExpressionValue() != null);
  }

  /** The constants case {@code c} names; none for a default. Refuses {@code case null}. */
  private static List<String> constants(SwitchCase c, ITypeBinding e, String name) {
    List<String> out = new ArrayList<>();
    for (Object o : c.expressions()) {
      if (o instanceof NullLiteral) {
        throw new Refusal(
            "case null cannot move into "
                + e.getName()
                + ": "
                + name
                + "() cannot be called on null. Test for null before the switch first, and take the"
                + " case out of it.");
      }
      if (o instanceof Name n
          && n.resolveBinding() instanceof IVariableBinding b
          && b.isEnumConstant()) {
        out.add(b.getName());
      }
    }
    return out;
  }

  /**
   * Refuses a static field or method {@code body} names bare that {@code e} does not see by that
   * name ({@link #sees}): in the enum that name would mean something else, or nothing.
   */
  private static void checkStatics(ASTNode body, String label, ITypeBinding e) {
    for (SimpleName n : Nodes.all(body, SimpleName.class)) {
      ITypeBinding owner =
          n.resolveBinding() instanceof IVariableBinding b
                  && b.isField()
                  && Modifier.isStatic(b.getModifiers())
                  && !TypeSwitch.qualifiedByOther(n)
              ? b.getDeclaringClass()
              : n.resolveBinding() instanceof IMethodBinding m
                      && Modifier.isStatic(m.getModifiers())
                      && n.getParent() instanceof MethodInvocation call
                      && call.getName() == n
                      && call.getExpression() == null
                  ? m.getDeclaringClass()
                  : null;
      if (owner == null || sees(e, owner, n)) {
        continue;
      }
      String called = n.resolveBinding() instanceof IMethodBinding ? "(...)" : "";
      throw new Refusal(
          "the case of "
              + label
              + " uses "
              + n.getIdentifier()
              + called
              + ", a static of "
              + owner.getErasure().getName()
              + ", which "
              + e.getName()
              + " does not see by that name. Qualify it first ("
              + owner.getErasure().getName()
              + "."
              + n.getIdentifier()
              + called
              + "), with replace_expression.");
    }
  }

  /** The declaration of the enum {@code e}, top-level or a member, in {@code unit}. */
  private static EnumDeclaration declaration(CompilationUnit unit, ITypeBinding e) {
    if (TypeSwitch.declaration(unit, e) instanceof EnumDeclaration d) {
      return d;
    }
    throw new Refusal(e.getName() + " is not an enum of its file.");
  }

  /**
   * Whether {@code e} sees the static {@code n} of {@code owner} by its simple name: {@code owner}
   * is {@code e} or a type around it, and no type before it on the way out declares a member of
   * that name. A member a type inherits is left to {@code NameCheck}.
   */
  private static boolean sees(ITypeBinding e, ITypeBinding owner, SimpleName n) {
    String key = owner.getErasure().getKey();
    boolean method = n.resolveBinding() instanceof IMethodBinding;
    for (ITypeBinding t = e; t != null; t = t.getDeclaringClass()) {
      if (t.getErasure().getKey().equals(key)) {
        return true;
      }
      for (IBinding m : method ? t.getDeclaredMethods() : t.getDeclaredFields()) {
        if (m.getName().equals(n.getIdentifier())) {
          return false;
        }
      }
    }
    return false;
  }
}
