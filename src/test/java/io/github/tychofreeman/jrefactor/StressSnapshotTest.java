package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StressSnapshotTest {

  @Test
  void aKilledRunIsPutBackAtTheNextStart(@TempDir Path root) throws Exception {
    Path a = root.resolve("com/example/A.java");
    Files.createDirectories(a.getParent());
    Files.writeString(a, "class A {}\n");
    StressSnapshot.take(root, List.of(a));

    // What a run killed mid-edit leaves: a changed file and a new one.
    Files.writeString(a, "class A { int zq; }\n");
    Path b = root.resolve("com/example/B.java");
    Files.writeString(b, "class B {}\n");

    assertEquals(2, StressSnapshot.recover(root));
    assertEquals("class A {}\n", Files.readString(a));
    assertFalse(Files.exists(b));
    assertEquals(0, StressSnapshot.recover(root));
  }

  @Test
  void aFinishedRunLeavesNoSnapshot(@TempDir Path root) throws Exception {
    Path a = root.resolve("A.java");
    Files.writeString(a, "class A {}\n");
    StressSnapshot.take(root, List.of(a));
    StressSnapshot.done(root);
    Files.writeString(a, "class A { int changedLater; }\n");

    assertEquals(0, StressSnapshot.recover(root));
    assertEquals("class A { int changedLater; }\n", Files.readString(a));
  }
}
