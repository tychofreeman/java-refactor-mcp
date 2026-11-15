package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SideEffectsTest {

  private static final String A = "src/main/java/p/A.java";
  private static final String B = "src/main/java/p/B.java";

  /** The outline of {@code file}, and how many binding parses it took. */
  private static String outline(SourceProject project, String file, int parses) throws Exception {
    int before = project.session().bindingParses;
    String out = Inspect.file(project, file);
    assertEquals(parses, project.session().bindingParses - before, out);
    return out;
  }

  @Test
  void aSecondOutlineReadsTheCalledFilesFromTheSessionUntilOneChanges(@TempDir Path root)
      throws Exception {
    SourceProject project = TestProjects.maven(root, false);
    TestProjects.write(root, A, "package p;\n\nclass A {\n  void run() {\n    B.go();\n  }\n}\n");
    TestProjects.write(
        root,
        "src/main/java/p/C.java",
        "package p;\n\nclass C {\n  void also() {\n    B.go();\n  }\n}\n");
    TestProjects.write(root, B, "package p;\n\nclass B {\n  static void go() {}\n}\n");

    assertTrue(outline(project, A, 2).contains("no side effects"));
    outline(project, A, 1);
    assertTrue(outline(project, "src/main/java/p/C.java", 1).contains("no side effects"));

    Journal.put(
        project,
        root.resolve(B),
        "package p;\n\nclass B {\n  static int n;\n\n  static void go() {\n    n++;\n  }\n}\n");
    assertTrue(outline(project, A, 2).contains("side effects: calls B.go, which assigns to n"));

    TestProjects.write(root, B, "package p;\n\nclass B {\n  static void go() {}\n}\n");
    assertTrue(outline(project, A, 2).contains("no side effects"));
  }
}
