package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
}
