package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeParametersTest {

  @TempDir Path dir;

  /** A golden case has one source root; a Maven project's test root is walked too. */
  @Test
  void aUseInTheTestRootPassesTheTypeArgument() throws Exception {
    write("pom.xml", "<project>not a build Maven can run</project>");
    write("src/main/java/a/Box.java", "package a;\n\npublic class Box {\n  public Box() {}\n}\n");
    write(
        "src/test/java/a/BoxTest.java",
        "package a;\n\nclass BoxTest {\n  Box box = new Box();\n  Object o = new Box();\n}\n");
    SourceProject project = SourceProject.load(dir);

    Outcome outcome =
        Tools.call(
            Tools.ADD_PARAMETER,
            project,
            Map.of(
                "file", "src/main/java/a/Box.java",
                "name", "Box",
                "type_parameter", "T",
                "value", "String"));

    assertTrue(outcome.text().contains("src/test/java/a/BoxTest.java"), outcome.text());
    assertEquals(
        "package a;\n\nclass BoxTest {\n  Box<String> box = new Box<>();\n"
            + "  Object o = new Box<String>();\n}\n",
        Files.readString(dir.resolve("src/test/java/a/BoxTest.java")));
    assertEquals(
        "package a;\n\npublic class Box<T> {\n  public Box() {}\n}\n",
        Files.readString(dir.resolve("src/main/java/a/Box.java")));
  }

  private void write(String rel, String text) throws IOException {
    Path file = dir.resolve(rel);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }
}
