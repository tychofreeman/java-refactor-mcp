package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtTest {

  @TempDir Path dir;

  private static final String UTIL =
      "package p;\n\npublic class Util {\n  public static int twice(int x) {\n"
          + "    return 2 * x;\n  }\n}\n";

  /** A type the batch read from the source path keeps its members after the batch (#431). */
  @Test
  void aTypeFromTheSourcePathKeepsItsMethodsAfterTheBatch() throws Exception {
    write("p/Util.java", UTIL);
    Path use = write("p/Use.java", "package p;\n\nclass Use {\n  int four = Util.twice(2);\n}\n");
    SourceProject project = SourceProject.of(dir);

    CompilationUnit unit = Jdt.parseAll(project, List.of(use)).get(use);

    MethodInvocation call = Nodes.all(unit, MethodInvocation.class).get(0);
    assertEquals(2, call.resolveMethodBinding().getDeclaringClass().getDeclaredMethods().length);
  }

  private Path write(String rel, String text) throws IOException {
    Path file = dir.resolve(rel);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    return file;
  }

  /** {@link Jdt#parse} of one file keeps a source-path type's members too. */
  @Test
  void aTypeFromTheSourcePathKeepsItsMethodsAfterParse() throws Exception {
    write("p/Util.java", UTIL);
    String text = "package p;\n\nclass Use {\n  int four = Util.twice(2);\n}\n";
    Path use = write("p/Use.java", text);

    CompilationUnit unit = Jdt.parse(SourceProject.of(dir), use, text);

    MethodInvocation call = Nodes.all(unit, MethodInvocation.class).get(0);
    assertEquals(2, call.resolveMethodBinding().getDeclaringClass().getDeclaredMethods().length);
  }

  /**
   * A probe field goes where a member can: after an enum's constants, inside a wrapped header's
   * body.
   */
  @Test
  void aProbeFieldIsAMemberOfTheType() {
    String[] sources = {
      "enum E {\n  A,\n  B;\n\n  void f() {}\n}\n",
      "enum E {\n  A,\n  B\n}\n",
      "enum E { A(1) { void f() {} }, B(2), }\n",
      "class C\n    extends Object {\n  int x;\n}\n",
      "class C { int x; }\n",
      "record R(int a) implements Runnable {\n  public void run() {}\n}\n",
      "interface I {}\n",
    };
    for (String text : sources) {
      CompilationUnit before = Jdt.parseSyntax(text);
      String probed = Jdt.withProbe(text, (AbstractTypeDeclaration) before.types().get(0), "Thing");
      CompilationUnit after = Jdt.parseSyntax(probed);
      assertEquals(0, after.getProblems().length, probed);
      AbstractTypeDeclaration type = (AbstractTypeDeclaration) after.types().get(0);
      assertTrue(
          ((List<?>) type.bodyDeclarations())
              .stream()
                  .anyMatch(
                      d -> d instanceof FieldDeclaration f && f.toString().contains(Jdt.PROBE)),
          probed);
    }
  }

  /** The probe resolves a name as the type's members see it, imports fixed. */
  @Test
  void aProbeResolvesANameInTheTypesScope() throws Exception {
    String text =
        "package p;\n\nenum E {\n  A,\n  B;\n\n  static class Busy extends Exception {}\n}\n";
    Path file = write("p/E.java", text);
    SourceProject project = SourceProject.of(dir);
    CompilationUnit unit = Jdt.parse(project, file, text);
    AbstractTypeDeclaration e = (AbstractTypeDeclaration) unit.types().get(0);

    assertEquals(
        "p.E.Busy", Jdt.probeType(project, file, text, e, "Busy", List.of()).getQualifiedName());
    assertEquals(
        "java.io.IOException",
        Jdt.probeType(project, file, text, e, "IOException", List.of()).getQualifiedName());
  }
}
