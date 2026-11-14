package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EffectsTest {

  private static final String SHOP =
      "package com.example;\n\nimport java.util.function.IntSupplier;\n\npublic class Shop {\n"
          + "  static final int TWO = 2;\n  int n;\n  int[] a;\n\n"
          + "  int bump() {\n    return ++n;\n  }\n\n"
          + "  int f(int x) {\n    return x + TWO;\n  }\n\n"
          + "  int g(int x) {\n    return bump() + n + a[x];\n  }\n\n"
          + "  IntSupplier h(int x) {\n    return () -> bump() + x;\n  }\n}\n";

  private static final String GUARD =
      "package com.example;\n\npublic class Guard {\n  static final RuntimeException ERR = null;\n\n"
          + "  int f(int n, Integer boxed) {\n"
          + "    if (n == 0) throw ERR;\n"
          + "    assert n > 0;\n"
          + "    n /= 2;\n"
          + "    try { throw new IllegalStateException(); } catch (RuntimeException e) { }\n"
          + "    return boxed;\n"
          + "  }\n}\n";

  private static final String JUMPS =
      "class J {\n  int f(int[] xs) {\n    outer:\n    for (int x : xs) {\n      {\n"
          + "        for (int i = 0; i < x; i++) {\n          if (i > 1) break outer;\n"
          + "          if (i > 0) break;\n        }\n"
          + "        class L {\n          int g() {\n            return 1;\n          }\n        }\n"
          + "      }\n    }\n"
          + "    if (xs.length > 0) {\n      return 1;\n    } else {\n"
          + "      throw new IllegalStateException();\n    }\n  }\n}\n";

  private static Opened open(Path root) throws Exception {
    Path src = root.resolve("com/example");
    Files.createDirectories(src);
    Files.writeString(src.resolve("Shop.java"), SHOP);
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    return Opened.of(project, "com/example/Shop.java");
  }

  /** The expression returned on {@code line}. */
  private static Expression returned(Opened opened, int line) {
    return opened
        .startingOn(line, ReturnStatement.class, r -> true, "return", "", "the return")
        .getExpression();
  }

  @Test
  void aLocalAndAConstantAreFixed(@TempDir Path root) throws Exception {
    Effects e = Effects.of(returned(open(root), 15));
    assertTrue(e.fixed());
    assertFalse(e.changes());
    assertEquals(List.of("x", "TWO"), e.named().stream().map(v -> v.getName()).toList());
  }

  @Test
  void aCallAFieldAndAnElementInOrder(@TempDir Path root) throws Exception {
    Effects e = Effects.of(returned(open(root), 19));
    assertFalse(e.fixed());
    assertTrue(e.changes());
    assertEquals(
        List.of(
            Effects.Kind.CALL,
            Effects.Kind.NAME,
            Effects.Kind.ELEMENT,
            Effects.Kind.MAY_THROW,
            Effects.Kind.NAME,
            Effects.Kind.NAME),
        e.all().stream().map(Effects.Effect::kind).toList());
    assertEquals(1, e.normalFlowChanges().size());
  }

  @Test
  void aLambdaRunsLater(@TempDir Path root) throws Exception {
    Effects e = Effects.of(returned(open(root), 23));
    assertTrue(e.fixed());
    assertTrue(e.changes());
    assertTrue(e.normalFlowChanges().isEmpty());
  }

  @Test
  void anIncrementWritesItsField(@TempDir Path root) throws Exception {
    Opened opened = open(root);
    Effects e = Effects.of(returned(opened, 11));
    assertEquals(List.of("n"), e.written().stream().map(v -> v.getName()).toList());
    assertTrue(e.writes(e.written().iterator().next(), 0));
  }

  /** The statement of the method body that starts on {@code line} of the guard source. */
  private static Statement statementOn(Path root, int line) throws Exception {
    Path src = root.resolve("com/example");
    Files.createDirectories(src);
    Files.writeString(src.resolve("Guard.java"), GUARD);
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    return Opened.of(project, "com/example/Guard.java")
        .startingOn(
            line,
            Statement.class,
            s -> s.getParent().getParent() instanceof MethodDeclaration,
            "statement",
            "",
            "the statement");
  }

  @Test
  void aThrowAnAssertADivisionAndAnUnboxingMayThrow(@TempDir Path root) throws Exception {
    for (int line = 7; line <= 11; line++) {
      assertFalse(Effects.of(statementOn(root, line)).mayThrow().isEmpty(), "line " + line);
    }
  }

  @Test
  void aThrowLeavesUnlessATryInsideCatchesIt(@TempDir Path root) throws Exception {
    assertTrue(Effects.leaves(statementOn(root, 7), true));
    assertFalse(Effects.leaves(statementOn(root, 10), true));
    assertFalse(Effects.leaves(statementOn(root, 7), false));
  }

  @Test
  void aJumpExitsWhatItDoesNotLandIn() {
    CompilationUnit unit = Jdt.parseSyntax(JUMPS);
    Block inner = Nodes.all(unit, Block.class).get(2);
    List<BreakStatement> breaks = Nodes.all(unit, BreakStatement.class);
    assertTrue(Effects.exits(breaks.get(0), inner), "break outer");
    assertFalse(Effects.exits(breaks.get(1), inner), "break of the inner for");
    List<ReturnStatement> returns = Nodes.all(unit, ReturnStatement.class);
    assertFalse(Effects.exits(returns.get(0), inner), "return of a local class");
    assertTrue(Effects.exits(returns.get(1), Nodes.all(unit, IfStatement.class).get(2)));
  }

  @Test
  void completesNormallyUnlessEveryPathJumps() {
    CompilationUnit unit = Jdt.parseSyntax(JUMPS);
    List<IfStatement> ifs = Nodes.all(unit, IfStatement.class);
    assertTrue(Effects.completesNormally(ifs.get(0)), "an if without else");
    assertFalse(Effects.completesNormally(ifs.get(2)), "return or throw");
    assertFalse(Effects.completesNormally(Nodes.all(unit, Block.class).get(0)), "the body");
    assertTrue(Effects.completesNormally(Nodes.all(unit, Block.class).get(2)), "a class last");
  }
}
