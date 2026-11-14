package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.SimpleName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NameCheckTest {

  private static final String SHAPE =
      """
      package p;

      class %s {
        Runnable r =
            new Runnable() {
              public void run() {
                int n = 1;
                use(n);
              }
            };

        static void use(int x) {}
      }
      """;

  /**
   * A name moved from one file into another, where a local of the same name stands at the same
   * offset in an anonymous class, means that other local: the two files' positions are not one
   * position, so the capture is refused.
   */
  @Test
  void aDeclarationAtTheSameOffsetInAnotherFileIsNotTheSame(@TempDir Path root) throws Exception {
    Path a = root.resolve("p/A.java");
    Path b = root.resolve("p/B.java");
    Files.createDirectories(a.getParent());
    String textA = SHAPE.formatted("A");
    String textB = SHAPE.formatted("B");
    Files.writeString(a, textA);
    Files.writeString(b, textB);
    SourceProject project = SourceProject.of(root);

    CompilationUnit unitA = Jdt.parse(project, a, textA);
    int use = textA.indexOf("use(n)") + "use(".length();
    SimpleName moved =
        Nodes.all(unitA, SimpleName.class).stream()
            .filter(n -> n.getStartPosition() == use)
            .findFirst()
            .orElseThrow();
    int at = textB.indexOf("use(n)") + "use(".length();

    assertThrows(
        Refusal.class,
        () ->
            NameCheck.require(
                project,
                Map.of(b, textB),
                Map.of(b, List.of(new TextEdit(at, at + 1, "n"))),
                NameCheck.meanings(List.of(moved)),
                "moving n"));
  }

  /**
   * "a.count + count" with both names renamed to total: each written name's start, and an offset
   * after them, map back to where they were.
   */
  @Test
  void anOffsetAfterEditsIsWhereItWasBeforeThem() {
    List<TextEdit> renames = List.of(new TextEdit(2, 7, "total"), new TextEdit(10, 15, "total"));
    assertEquals(2, NameCheck.before(renames, 2));
    assertEquals(10, NameCheck.before(renames, 10));
    assertEquals(16, NameCheck.before(renames, 16));
    List<TextEdit> longer = List.of(new TextEdit(2, 7, "totals"), new TextEdit(10, 15, "totals"));
    assertEquals(10, NameCheck.before(longer, 11));
    assertEquals(16, NameCheck.before(longer, 18));
  }
}
