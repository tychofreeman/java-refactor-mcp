package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ReturnStatement;
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
}
