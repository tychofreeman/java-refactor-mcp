package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenBuildTest {

  private static final String CHILD =
      """
      <project>
        <modelVersion>4.0.0</modelVersion>
        <parent>
          <groupId>g</groupId>
          <artifactId>parent</artifactId>
          <version>1</version>
          <relativePath>../pom.xml</relativePath>
        </parent>
        <artifactId>child</artifactId>
      </project>
      """;

  @Test
  void aChangedParentPomChangesTheCacheKey(@TempDir Path dir) throws Exception {
    Path parent = dir.resolve("pom.xml");
    Path root = Files.createDirectories(dir.resolve("child"));
    Files.writeString(root.resolve("pom.xml"), CHILD);
    Files.writeString(parent, "<project><version>1</version></project>");
    Path cache = dir.resolve("cache.txt");

    MavenBuild.remember(root, cache, List.of(dir.toString()));
    String before = Files.readAllLines(cache).get(0);
    Files.writeString(parent, "<project><version>2</version></project>");
    MavenBuild.remember(root, cache, List.of(dir.toString()));
    String after = Files.readAllLines(cache).get(0);

    assertNotEquals(before, after);
  }
}
