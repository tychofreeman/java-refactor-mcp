package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HoldingTest {

  @TempDir Path dir;

  @Test
  void ofTakesTheFilesWithTheNameAndEachFileGivenWhateverItHolds() throws IOException {
    write("src/main/java/p/A.java", "package p; class A { Cart cart; }");
    write("src/main/java/p/B.java", "package p; class B {}");
    write("src/main/java/p/C.java", "package p; class C {}");
    write("src/main/java/p/Cart.java", "package p; class Cart {}");
    write("src/main/java/p/D.java", "package p; class D {}");
    SourceProject project = SourceProject.load(dir);

    Holding holding =
        Holding.of(
            project,
            "Cart",
            dir.resolve("src/main/java/p/../p/D.java"),
            project.javaFile("src/main/java/p/B.java"));

    assertEquals(
        List.of("A.java", "B.java", "Cart.java", "D.java"), names(holding.texts().keySet()));
    assertEquals(names(holding.texts().keySet()), names(holding.units().keySet()));
    holding.units().values().forEach(u -> assertNotNull(u.getPackage()));
    assertEquals(
        "package p; class B {}", holding.texts().get(project.javaFile("src/main/java/p/B.java")));
  }

  @Test
  void whereTakesTheFilesWhosePathAndTextItAccepts() throws IOException {
    write("src/main/java/p/A.java", "package p; class A { Runnable r = () -> {}; }");
    write("src/main/java/p/B.java", "package p; class B { Runnable r = () -> {}; }");
    write("src/main/java/p/C.java", "package p; class C {}");
    SourceProject project = SourceProject.load(dir);
    Path a = project.javaFile("src/main/java/p/A.java");

    Holding holding = Holding.where(project, (f, t) -> !f.equals(a) && t.contains("->"));

    assertEquals(List.of("B.java"), names(holding.units().keySet()));
  }

  private static List<String> names(java.util.Collection<Path> files) {
    return files.stream().map(f -> f.getFileName().toString()).toList();
  }

  private void write(String rel, String text) throws IOException {
    Path f = dir.resolve(rel);
    Files.createDirectories(f.getParent());
    Files.writeString(f, text);
  }
}
