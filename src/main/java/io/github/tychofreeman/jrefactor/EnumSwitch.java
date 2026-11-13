package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.YieldStatement;

/**
 * type_switch_to_method over an enum of the project: each constant takes its case's value. When
 * every case of a switch expression gives a constant (or null), the enum gains a field of the
 * switch's type, which its constructor sets ({@link EnumField}), and the switch becomes a call of
 * the field's accessor.
 */
final class EnumSwitch {
  private EnumSwitch() {}

  static Outcome run(
      SourceProject project,
      Opened opened,
      ASTNode node,
      Expression selector,
      ITypeBinding e,
      String name,
      int line)
      throws Exception {
    Path eFile = TypeSwitch.fileOf(project, e);
    if (eFile == null) {
      throw new Refusal(
          "the switch is over "
              + e.getName()
              + ", which is not a top-level enum of the project's to declare "
              + name
              + " on.");
    }
    if (!(node instanceof SwitchExpression sw) || sw.resolveTypeBinding() == null) {
      throw new Refusal(
          "a switch statement over an enum becomes a method in each constant, which"
              + " type_switch_to_method does not write yet; a switch expression whose cases each"
              + " give a constant becomes a field.");
    }
    String text = opened.text();
    String v = selector instanceof SimpleName n ? n.getIdentifier() : null;
    Map<String, Expression> cases = new LinkedHashMap<>();
    Expression fallback = null;
    List<?> statements = sw.statements();
    for (int i = 0; i < statements.size(); i++) {
      if (!(statements.get(i) instanceof SwitchCase c)) {
        continue;
      }
      Object next = i + 1 < statements.size() ? statements.get(i + 1) : null;
      if (!c.isSwitchLabeledRule()
          || !(next instanceof YieldStatement y)
          || y.getExpression() == null) {
        throw new Refusal(
            "each case must be an arrow case giving an expression; the one at "
                + c.toString().strip()
                + " is not.");
      }
      Expression value = y.getExpression();
      List<String> constants = constants(c, e, name);
      String label = constants.isEmpty() ? "default" : constants.get(0);
      TypeSwitch.usesOnly(value, v, label, "the switch's value");
      checkStatics(value, label, e);
      if (!(value instanceof NullLiteral) && value.resolveConstantExpressionValue() == null) {
        throw new Refusal(
            "the case of "
                + label
                + " gives "
                + text.substring(value.getStartPosition(), Nodes.end(value))
                + ", which is not a constant; type_switch_to_method writes a field from cases"
                + " that each give a constant (or null), and does not yet write a method in each"
                + " constant.");
      }
      if (constants.isEmpty()) {
        fallback = value;
      }
      for (String k : constants) {
        cases.put(k, value);
      }
    }

    Map<Path, String> texts = new LinkedHashMap<>();
    texts.put(opened.file(), text);
    texts.putIfAbsent(eFile, Files.readString(eFile));
    Map<Path, CompilationUnit> parsed = Jdt.parseAll(project, new ArrayList<>(texts.keySet()));
    EnumDeclaration target = declaration(parsed.get(eFile), e);
    Map<String, String> values = new LinkedHashMap<>();
    for (Object o : target.enumConstants()) {
      String k = ((EnumConstantDeclaration) o).getName().getIdentifier();
      Expression x = cases.getOrDefault(k, fallback);
      values.put(k, text.substring(x.getStartPosition(), Nodes.end(x)));
    }
    String type = Extract.typeName(sw.resolveTypeBinding());
    String eText = texts.get(eFile);
    EnumField.Field field = EnumField.field(target, name, null, values, eText, type);

    NameCheck.require(
        project,
        texts,
        Map.of(eFile, field.edits()),
        NameCheck.meanings(new ArrayList<>(cases.values())),
        "moving the cases' values into " + e.getName());

    String sel = text.substring(selector.getStartPosition(), Nodes.end(selector));
    String call =
        (selector instanceof ThisExpression
                ? ""
                : (selector instanceof Name ? sel : "(" + sel + ")") + ".")
            + name
            + "()";
    TextEdit replaced = new TextEdit(node.getStartPosition(), Nodes.end(node), call);
    List<TextEdit> eEdits = new ArrayList<>(field.edits());
    Map<Path, List<TextEdit>> edits = new LinkedHashMap<>();
    if (eFile.equals(opened.file())) {
      eEdits.add(replaced);
    } else {
      edits.put(opened.file(), List.of(replaced));
    }
    eEdits.sort((a, b) -> b.start() - a.start());
    String withValues = TextEdit.apply(eText, eEdits, 0);
    String out =
        Declarations.plan(
                withValues, project.relative(eFile).toString(), Names.typeLabel(e), field.members())
            .text();
    edits.put(eFile, List.of(new TextEdit(0, eText.length(), out)));
    Stubs.Declared declared =
        MemberMove.gate(
            project,
            texts,
            edits,
            "turning the switch into " + name + "()",
            "Check that each case's value type-checks as " + type + " in " + e.getName() + ".");
    int count = values.size();
    return new Outcome(
        "added the field "
            + type
            + " "
            + name
            + " to "
            + e.getName()
            + ", read by "
            + name
            + "(); "
            + count
            + (count == 1
                ? " constant passes its case's value"
                : " constants pass their case's value")
            + "; the code at line "
            + line
            + " of "
            + opened.rel()
            + " is now "
            + call,
        declared);
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
   * Refuses a static field {@code value} names bare that {@code e} does not declare: in the enum
   * that name would mean something else, or nothing.
   */
  private static void checkStatics(Expression value, String label, ITypeBinding e) {
    for (SimpleName n : Nodes.all(value, SimpleName.class)) {
      if (n.resolveBinding() instanceof IVariableBinding b
          && b.isField()
          && Modifier.isStatic(b.getModifiers())
          && !TypeSwitch.qualifiedByOther(n)
          && b.getDeclaringClass() != null
          && !b.getDeclaringClass().getErasure().getKey().equals(e.getErasure().getKey())) {
        String owner = b.getDeclaringClass().getErasure().getName();
        throw new Refusal(
            "the case of "
                + label
                + " uses "
                + n.getIdentifier()
                + ", a static of "
                + owner
                + ", which "
                + e.getName()
                + " does not see by that name. Qualify it first ("
                + owner
                + "."
                + n.getIdentifier()
                + "), with replace_expression.");
      }
    }
  }

  /** The declaration of the top-level enum {@code e} in {@code unit}. */
  private static EnumDeclaration declaration(CompilationUnit unit, ITypeBinding e) {
    for (Object o : unit.types()) {
      if (o instanceof EnumDeclaration d
          && d.getName().getIdentifier().equals(e.getErasure().getName())) {
        return d;
      }
    }
    throw new Refusal(e.getName() + " is not a top-level enum of its file.");
  }
}
