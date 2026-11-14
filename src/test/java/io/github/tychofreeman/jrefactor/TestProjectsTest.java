package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestProjectsTest {

  @Test
  void writeCreatesTheDirectoriesAndReturnsTheFile(@TempDir Path root) throws Exception {
    Path file = TestProjects.write(root, "com/example/A.java", "class A {}\n");
    assertEquals(root.resolve("com/example/A.java"), file);
    assertEquals("class A {}\n", Files.readString(file));
  }

  @Test
  void mavenHasAMainAndATestRoot(@TempDir Path root) throws Exception {
    SourceProject project = TestProjects.maven(root, true);
    assertEquals(
        List.of(root.resolve("src/main/java"), root.resolve("src/test/java")),
        project.sourceRoots());
    assertTrue(Files.isDirectory(root.resolve("src/test/java")));
    assertTrue(project.session().editing);
  }

  @Test
  void copyTreeCopiesEveryFile(@TempDir Path root) throws Exception {
    TestProjects.write(root, "in/a/B.txt", "b");
    TestProjects.copyTree(root.resolve("in"), root.resolve("out"));
    assertEquals("b", Files.readString(root.resolve("out/a/B.txt")));
  }
}
