package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardsTest {

  /** Each case: a method's parameters, a guard, and code under it that may throw. */
  private static final List<List<String>> CASES =
      List.of(
          List.of("int n, int d", "d == 0", "n / d"),
          List.of("int n, int d", "0 != d", "n % d"),
          List.of("int n, int d", "d > 0", "n / d"),
          List.of("int n", "this.d != 0", "n / d"),
          List.of("Calc c", "c != null", "c.cost"),
          List.of("Calc c", "null == c", "c.cost"),
          List.of("Calc c", "Objects.nonNull(c)", "c.cost"),
          List.of("Calc c", "Objects.isNull(c)", "c.cost"),
          List.of("Calc c", "c instanceof Calc", "c.cost"),
          List.of("Calc c", "c instanceof Calc k", "c.cost"),
          List.of("Calc c", "c.next != null", "c.next.cost"),
          List.of("int n, int d", "n == 0", "n / d"),
          List.of("int n, int d, boolean on", "on", "n / d"),
          List.of("int n, int d, int e", "e != 0", "n / d"),
          List.of("Calc c, Calc e", "e != null", "c.cost"),
          List.of("int[] a, boolean on", "on", "a[0]"));

  /** The guard and the code of each case, parsed. */
  private static List<ConditionalExpression> open(Path root) throws Exception {
    StringBuilder src =
        new StringBuilder(
            "package com.example;\n\nimport java.util.Objects;\n\npublic class Calc {\n"
                + "  int d;\n  Calc next;\n  int cost;\n");
    for (int i = 0; i < CASES.size(); i++) {
      List<String> c = CASES.get(i);
      src.append("  int m" + i + "(" + c.get(0) + ") {\n")
          .append("    return " + c.get(1) + " ? " + c.get(2) + " : 0;\n")
          .append("  }\n");
    }
    src.append("}\n");
    Path dir = root.resolve("com/example");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("Calc.java"), src.toString());
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    Opened opened = Opened.of(project, "com/example/Calc.java");
    return Nodes.all(opened.unit(), ReturnStatement.class).stream()
        .map(r -> (ConditionalExpression) r.getExpression())
        .toList();
  }

  private static Boolean tested(ConditionalExpression c) {
    List<Guards.Operand> operands = Guards.operands(Effects.of(c.getThenExpression()).mayThrow());
    return operands == null ? null : Guards.tests(List.of(c.getExpression()), operands);
  }

  @Test
  void aGuardKeepsADivisionOrADereferenceOnlyWhenItTestsTheOperand(@TempDir Path root)
      throws Exception {
    List<ConditionalExpression> cases = open(root);
    assertEquals(
        List.of(
            true, true, true, true, true, true, true, true, true, true, true, false, false, false,
            false),
        cases.subList(0, 15).stream().map(GuardsTest::tested).toList());
  }

  @Test
  void anArrayElementHasNoOperandAGuardMustTest(@TempDir Path root) throws Exception {
    assertNull(tested(open(root).get(15)));
  }
}
