package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.json.McpJsonDefaults;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs each directory under src/test/resources/golden as one case.
 *
 * <p>A case has {@code in/} (the project before), {@code args.properties} (the call: {@code tool},
 * default {@code rename}, and its arguments; {@code <arg>.txt} files give arguments with several
 * lines), and {@code out/} (the project after; without it, the project must be unchanged), {@code
 * result.txt} (the text the tool returns), or {@code refused.txt} (a fragment the refusal must
 * contain; the project must be unchanged).
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

  private static void runCase(Path caseDir) throws Exception {
    Path work = WORK.resolve(caseDir.getFileName().toString());
    deleteTree(work);
    copyTree(caseDir.resolve("in"), work);

    Properties args = new Properties();
    try (Reader reader = Files.newBufferedReader(caseDir.resolve("args.properties"))) {
      args.load(reader);
    }
    String tool = args.getProperty("tool", "rename");
    SourceProject project = SourceProject.of(work);

    Path refused = caseDir.resolve("refused.txt");
    Path result = caseDir.resolve("result.txt");
    if (Files.exists(refused)) {
      String expected = Files.readString(refused).strip();
      Refusal refusal = assertThrows(Refusal.class, () -> call(tool, project, args, caseDir));
      assertTrue(
          refusal.getMessage().contains(expected),
          () -> "expected refusal containing '" + expected + "', got: " + refusal.getMessage());
      assertTreesEqual(caseDir.resolve("in"), work);
    } else {
      String text = call(tool, project, args, caseDir);
      Path out = caseDir.resolve("out");
      assertTreesEqual(Files.isDirectory(out) ? out : caseDir.resolve("in"), work);
      if (Files.exists(result)) {
        assertEquals(Files.readString(result), text);
      }
      assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project), "cached count");
      if (!Tools.named(tool).readOnly()) {
        Tools.call(Tools.named("undo"), project, Map.of());
        assertTreesEqual(caseDir.resolve("in"), work);
        assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project), "cached count");
      }
    }
  }

  /**
   * Calls the tool a case names through its definition, as the server does: properties are its
   * arguments (whole numbers and true/false as such), and each {@code <arg>.txt} file of the case
   * is that argument's text, for content with several lines. Returns the tool's text.
   */
  private static String call(String tool, SourceProject project, Properties props, Path caseDir)
      throws Exception {
    Tools.Def def = Tools.named(tool);
    Map<String, Object> values = new LinkedHashMap<>();
    for (String key : props.stringPropertyNames()) {
      if (!key.equals("tool")) {
        values.put(key, value(props.getProperty(key)));
      }
    }
    try (Stream<Path> files = Files.list(caseDir)) {
      for (Path f : files.sorted().toList()) {
        String name = f.getFileName().toString();
        if (name.endsWith(".txt") && !name.equals("refused.txt") && !name.equals("result.txt")) {
          values.put(name.substring(0, name.length() - 4), Files.readString(f));
        } else if (name.endsWith(".json")) {
          values.put(
              name.substring(0, name.length() - 5),
              McpJsonDefaults.getMapper().readValue(Files.readString(f), Object.class));
        }
      }
    }
    return Tools.call(def, project, values).render();
  }

  private static Object value(String text) {
    if (text.startsWith("[") && text.endsWith("]")) {
      return Arrays.stream(text.substring(1, text.length() - 1).split(","))
          .map(String::strip)
          .filter(s -> !s.isEmpty())
          .toList();
    }
    if (text.matches("-?\\d+")) {
      return Integer.valueOf(text);
    }
    if (text.equals("true") || text.equals("false")) {
      return Boolean.valueOf(text);
    }
    return text;
  }

  /** Every file in {@code expected} must match {@code actual}, and there must be no others. */
  private static void assertTreesEqual(Path expected, Path actual) throws IOException {
    Set<String> expectedFiles = javaFilesRelative(expected);
    assertEquals(expectedFiles, javaFilesRelative(actual), "set of files differs");
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
