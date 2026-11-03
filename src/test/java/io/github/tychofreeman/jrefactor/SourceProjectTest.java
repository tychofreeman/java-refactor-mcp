package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceProjectTest {

  @TempDir Path dir;

  @Test
  void mavenLayoutHasMainAndTestRootsOnly() throws IOException {
    write("pom.xml", POM);
    write("src/main/java/a/A.java", "package a; class A {}");
    write("src/test/java/a/ATest.java", "package a; class ATest {}");
    write("src/test/resources/golden/in/a/A.java", "package a; class A {}");

    SourceProject project = SourceProject.load(dir);

    assertEquals(
        List.of(dir.resolve("src/main/java"), dir.resolve("src/test/java")), project.sourceRoots());
    assertEquals(
        List.of("src/main/java/a/A.java", "src/test/java/a/ATest.java"),
        project.javaFiles().stream().map(project::relative).toList());
  }

  @Test
  void noBuildFileIsTheRootAlone() {
    SourceProject project = SourceProject.load(dir);
    assertEquals(List.of(dir.toAbsolutePath().normalize()), project.sourceRoots());
    assertEquals(List.of(), project.classpath());
  }

  /** Maven cannot build the pom here, so a classpath can only come from the cache. */
  @Test
  void aCachedClasspathIsUsedWhileThePomIsTheSame(@TempDir Path cacheDir) throws IOException {
    write("pom.xml", "<project>not a build Maven can run</project>");
    write("src/main/java/a/A.java", "package a; class A {}");
    Path jar = Files.createFile(cacheDir.resolve("lib.jar"));
    Path cache = cacheDir.resolve("classpath.txt");
    MavenBuild.remember(dir, cache, List.of(jar.toString()));

    SourceProject cached = SourceProject.load(dir, cache);
    assertEquals(List.of(jar.toString()), cached.classpath());
    assertEquals(null, cached.classpathProblem());

    write("pom.xml", "<project>changed</project>");
    SourceProject reloaded = SourceProject.load(dir, cache);
    assertEquals(List.of(), reloaded.classpath());
    assertTrue(reloaded.classpathProblem() != null);
  }

  /** This repo, loaded from its pom, resolves its dependencies and compiles without errors. */
  @Test
  void thisRepoTypeChecksClean() throws IOException {
    SourceProject project = SourceProject.load(Path.of(""));
    assertTrue(
        project.classpath().stream().anyMatch(p -> p.contains("org.eclipse.jdt.core")),
        () -> "classpath: " + project.classpath());
    Map<String, Integer> errors = TypeCheck.errors(project);
    assertEquals(Map.of(), errors);
  }

  private void write(String rel, String text) throws IOException {
    Path file = dir.resolve(rel);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private static final String POM =
      """
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>t</groupId>
        <artifactId>t</artifactId>
        <version>1</version>
      </project>
      """;
}
