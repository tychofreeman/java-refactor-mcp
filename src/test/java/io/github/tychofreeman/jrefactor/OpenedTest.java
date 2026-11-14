package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.IfStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenedTest {

  private static final String SHOP =
      "package com.example;\n\npublic class Shop {\n  int f(int x) {\n    if (x > 0) { if (x > 1) return 2; }\n    if (x < 0) {\n      return -1;\n    }\n    return 0;\n  }\n}\n";

  private static Opened open(Path root) throws Exception {
    Path src = root.resolve("com/example");
    Files.createDirectories(src);
    Files.writeString(src.resolve("Shop.java"), SHOP);
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    return Opened.of(project, "com/example/Shop.java");
  }

  @Test
  void findsATopLevelTypeAndRefusesAnother(@TempDir Path root) throws Exception {
    Opened opened = open(root);
    assertEquals("Shop", opened.topLevel("Shop").getName().getIdentifier());
    Refusal r = assertThrows(Refusal.class, () -> opened.topLevel("Cart"));
    assertEquals(
        "Cart is not a top-level type of com/example/Shop.java that resolves.", r.getMessage());
  }

  @Test
  void findsTheOneNodeOnALine(@TempDir Path root) throws Exception {
    Opened opened = open(root);
    IfStatement s =
        opened.startingOn(6, IfStatement.class, x -> true, "if statement", "", "the if");
    assertEquals(6, opened.unit().getLineNumber(s.getStartPosition()));
    Refusal none =
        assertThrows(
            Refusal.class,
            () -> opened.startingOn(7, IfStatement.class, x -> true, "if statement", "", "the if"));
    assertEquals(
        "no if statement starts on line 7 of com/example/Shop.java. Pass the line where the if starts.",
        none.getMessage());
    Refusal two =
        assertThrows(
            Refusal.class,
            () -> opened.startingOn(5, IfStatement.class, x -> true, "if statement", "", "the if"));
    assertEquals(
        "several if statements start on line 5 of com/example/Shop.java. Put each on a line of its own"
            + " first.",
        two.getMessage());
  }

  @Test
  void splitsAtTheFirstDot() {
    assertEquals(
        new Qualified("Sub", "m(Map.Entry)"), Qualified.of("Sub.m(Map.Entry)", "Type.method"));
    Refusal r =
        assertThrows(
            Refusal.class, () -> Qualified.of("m", "Type.method, the class and the method"));
    assertEquals("name is Type.method, the class and the method, not m.", r.getMessage());
  }

  @Test
  void requiresInOrderAndStopsAtTheFirstThatFails() {
    int[] checked = {0};
    Refusal r =
        assertThrows(
            Refusal.class,
            () ->
                Precondition.require(
                    Precondition.that(() -> ++checked[0] > 0, () -> "first"),
                    Precondition.that(() -> ++checked[0] < 0, () -> "second"),
                    Precondition.that(() -> ++checked[0] > 0, () -> "third")));
    assertEquals("second", r.getMessage());
    assertEquals(2, checked[0]);
  }

  @Test
  void namesTheKindOfATypeThatIsNotAClassOrInterface(@TempDir Path root) throws Exception {
    Path src = root.resolve("com/example");
    Files.createDirectories(src);
    Files.writeString(
        src.resolve("Shapes.java"),
        "package com.example;\n\nclass Shapes {}\n\nrecord Point(int x) {}\n\nenum Color { RED }\n");
    SourceProject project = new SourceProject(root, List.of(root), List.of(), null, new Session());
    Opened opened = Opened.of(project, "com/example/Shapes.java");
    Refusal record = assertThrows(Refusal.class, () -> opened.topLevel("Point"));
    assertEquals(
        "Point is a record, and this tool takes a class or interface here. Send the name of one:"
            + " com/example/Shapes.java declares Shapes.",
        record.getMessage());
    Refusal e = assertThrows(Refusal.class, () -> opened.topLevel("Color"));
    assertEquals(
        "Color is an enum, and this tool takes a class or interface here. Send the name of one:"
            + " com/example/Shapes.java declares Shapes.",
        e.getMessage());
  }

  @Test
  void picksTheNodeOnALineByColumn(@TempDir Path root) throws Exception {
    Opened opened = open(root);
    IfStatement inner =
        opened.startingOn(5, 18, IfStatement.class, x -> true, "if statement", "if statements");
    assertEquals(17, inner.getStartPosition() - opened.unit().getPosition(5, 0));
    Refusal wrong =
        assertThrows(
            Refusal.class,
            () ->
                opened.startingOn(
                    5, 7, IfStatement.class, x -> true, "if statement", "if statements"));
    assertEquals(
        "the if statement starts at columns 5 and 18 of line 5, not 7. Pass one of them.",
        wrong.getMessage());
    Refusal two =
        assertThrows(
            Refusal.class,
            () ->
                opened.startingOn(
                    5, null, IfStatement.class, x -> true, "if statement", "if statements"));
    assertEquals(
        "2 if statements start on line 5, at columns 5 and 18. Pass column, the one where the one"
            + " you mean starts.",
        two.getMessage());
  }
}
