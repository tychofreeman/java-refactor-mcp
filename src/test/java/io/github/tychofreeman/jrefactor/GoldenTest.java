package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs each directory under src/test/resources/golden as one case.
 *
 * <p>A case has {@code in/} (the project before), {@code args.properties} (the call), and either
 * {@code out/} (the project after) or {@code refused.txt} (a fragment the refusal message must
 * contain; the project must be unchanged afterwards).
 */
class GoldenTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden");
  private static final Path WORK = Path.of("target/golden");

  @TestFactory
  List<DynamicTest> cases() throws IOException {
    try (Stream<Path> dirs = Files.list(GOLDEN)) {
      return dirs.filter(Files::isDirectory)
          .sorted()
          .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> runCase(dir)))
          .toList();
    }
  }

  private static void runCase(Path caseDir) throws IOException {
    Path work = WORK.resolve(caseDir.getFileName().toString());
    deleteTree(work);
    copyTree(caseDir.resolve("in"), work);

    Properties args = new Properties();
    try (Reader reader = Files.newBufferedReader(caseDir.resolve("args.properties"))) {
      args.load(reader);
    }
    String file = args.getProperty("file");
    int line = Integer.parseInt(args.getProperty("line"));
    int column = Integer.parseInt(args.getProperty("column"));
    String newName = args.getProperty("newName");
    SourceProject project = SourceProject.of(work);

    Path refused = caseDir.resolve("refused.txt");
    if (Files.exists(refused)) {
      String expected = Files.readString(refused).strip();
      Refusal refusal =
          assertThrows(Refusal.class, () -> RenameLocal.run(project, file, line, column, newName));
      assertTrue(
          refusal.getMessage().contains(expected),
          () -> "expected refusal containing '" + expected + "', got: " + refusal.getMessage());
      assertTreesEqual(caseDir.resolve("in"), work);
    } else {
      RenameLocal.run(project, file, line, column, newName);
      assertTreesEqual(caseDir.resolve("out"), work);
    }
  }

  /**
   * Every .java file in {@code expected} must match {@code actual}, and there must be no others.
   */
  private static void assertTreesEqual(Path expected, Path actual) throws IOException {
    Set<String> expectedFiles = javaFilesRelative(expected);
    assertEquals(expectedFiles, javaFilesRelative(actual), "set of Java files differs");
    for (String rel : expectedFiles) {
      assertEquals(
          Files.readString(expected.resolve(rel)),
          Files.readString(actual.resolve(rel)),
          () -> "content differs: " + rel);
    }
  }

  private static Set<String> javaFilesRelative(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java"))
          .map(p -> root.relativize(p).toString().replace('\\', '/'))
          .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }
  }

  private static void copyTree(Path from, Path to) throws IOException {
    try (Stream<Path> files = Files.walk(from)) {
      for (Path src : files.filter(Files::isRegularFile).toList()) {
        Path dst = to.resolve(from.relativize(src).toString());
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst);
      }
    }
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> files = Files.walk(root)) {
      for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }
}
