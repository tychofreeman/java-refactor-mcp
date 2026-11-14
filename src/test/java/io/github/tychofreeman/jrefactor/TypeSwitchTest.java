package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.junit.jupiter.api.Test;

class TypeSwitchTest {

  private static final String SWITCHES =
      "class S {\n  int f(int k) {\n    switch (k) {\n      case 0 -> g();\n"
          + "      case 1 -> {\n        g();\n      }\n"
          + "      default -> throw new IllegalStateException();\n    }\n"
          + "    return switch (k) {\n      case 0 -> 1;\n"
          + "      case 1 -> {\n        yield 2;\n      }\n"
          + "      default -> throw new IllegalStateException();\n    };\n  }\n\n"
          + "  void g() {}\n}\n";

  @Test
  void anArmsBodyIsWhatFollowsTheArrow() {
    CompilationUnit unit = Jdt.parseSyntax(SWITCHES);
    List<?> statement = Nodes.all(unit, SwitchStatement.class).get(0).statements();
    assertEquals("g()", TypeSwitch.armBody(statement, 0, false, false).toString());
    assertEquals("{\n  g();\n}\n", TypeSwitch.armBody(statement, 2, false, false).toString());
    Refusal thrown =
        assertThrows(Refusal.class, () -> TypeSwitch.armBody(statement, 4, false, false));
    assertEquals(
        "each case must be an arrow case giving a statement or a block; the one at default -> is"
            + " not.",
        thrown.getMessage());
    assertEquals(
        "throw new IllegalStateException();\n",
        TypeSwitch.armBody(statement, 4, false, true).toString());
  }

  @Test
  void aBlockInASwitchExpressionIsABodyOnlyWithBlockOrThrow() {
    CompilationUnit unit = Jdt.parseSyntax(SWITCHES);
    List<?> value = Nodes.all(unit, SwitchExpression.class).get(0).statements();
    assertEquals("1", TypeSwitch.armBody(value, 0, true, false).toString());
    assertThrows(Refusal.class, () -> TypeSwitch.armBody(value, 2, true, false));
    assertEquals("{\n  yield 2;\n}\n", TypeSwitch.armBody(value, 2, true, true).toString());
  }
}
