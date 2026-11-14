package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CasesTest {

  @TempDir Path root;

  @BeforeEach
  void dirs() throws IOException {
    for (String name : List.of("rename_b", "rename_a", "inline_a")) {
      Files.createDirectory(root.resolve(name));
    }
    Files.writeString(root.resolve("rename_c"), "a file, not a case");
  }

  @Test
  void withoutPatternsSelectsEveryDirectorySorted() throws IOException {
    assertEquals(names("inline_a", "rename_a", "rename_b"), Cases.select(root, "p", null));
    assertEquals(names("inline_a", "rename_a", "rename_b"), Cases.select(root, "p", " "));
  }

  @Test
  void selectsByNameAndGlob() throws IOException {
    assertEquals(names("inline_a"), Cases.select(root, "p", "inline_a"));
    assertEquals(names("rename_a", "rename_b"), Cases.select(root, "p", "rename_*"));
    assertEquals(names("inline_a", "rename_b"), Cases.select(root, "p", "rename_b, inline_?"));
  }

  @Test
  void failsWhenAPatternMatchesNothing() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> Cases.select(root, "p", "rename_a,missing"));
    assertTrue(e.getMessage().contains("-Dp=rename_a,missing"), e.getMessage());
    assertTrue(e.getMessage().contains("missing"), e.getMessage());
  }

  private List<Path> names(String... names) {
    return List.of(names).stream().map(root::resolve).toList();
  }
}
