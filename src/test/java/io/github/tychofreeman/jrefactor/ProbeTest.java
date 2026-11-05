package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs each directory under src/test/resources/probes as one behavior probe: a program is run
 * before and after a tool, and what it printed compared. The type check sees only compile errors; a
 * probe sees a refactoring that compiles and changes what the program does.
 *
 * <p>A case has {@code in/} (a project whose {@code p.Main} prints what it did), {@code
 * args.properties} (the call, as a golden case's, with {@code <arg>.txt} files) and {@code
 * probe.properties}, whose {@code expect} is {@code applied} (the output must be the same), {@code
 * refused}, or {@code changed} (an edit meant to change it, which shows the probe sees a change).
 */
class ProbeTest {

  private static final Path PROBES = Path.of("src/test/resources/probes");
  private static final Path WORK = Path.of("target/probes");

  @TestFactory
  List<DynamicTest> probes() throws IOException {
    try (Stream<Path> dirs = Files.list(PROBES)) {
      return dirs.filter(Files::isDirectory)
          .sorted()
          .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> probe(dir)))
          .toList();
    }
  }

  private static void probe(Path caseDir) throws Exception {
    String name = caseDir.getFileName().toString();
    Path work = WORK.resolve(name).resolve("project");
    GoldenTest.deleteTree(WORK.resolve(name));
    GoldenTest.copyTree(caseDir.resolve("in"), work);
    Properties args = properties(caseDir.resolve("args.properties"));
    String expect = properties(caseDir.resolve("probe.properties")).getProperty("expect");
    String before = run(work, WORK.resolve(name).resolve("before"));
    SourceProject project = SourceProject.of(work);
    String outcome;
    try {
      outcome = GoldenTest.call(args.getProperty("tool"), project, args, caseDir).render();
    } catch (Refusal r) {
      assertEquals(expect, "refused", () -> "refused: " + r.getMessage());
      return;
    }
    String after = run(work, WORK.resolve(name).resolve("after"));
    String said = outcome;
    if (expect.equals("changed")) {
      assertNotEquals(before, after, () -> "the edit did not change the output: " + said);
    } else {
      assertEquals(before, after, () -> "behavior changed by: " + said);
      assertEquals(expect, "applied", () -> "applied: " + said);
    }
  }

  /**
   * What {@code p.Main} of the project at {@code root} prints, compiled into {@code classes} and
   * run in a class loader of its own (so its static state starts fresh); a compile error or an
   * exception is part of the output.
   */
  private static String run(Path root, Path classes) throws Exception {
    List<String> sources;
    try (Stream<Path> files = Files.walk(root)) {
      sources = files.filter(f -> f.toString().endsWith(".java")).map(Path::toString).toList();
    }
    Files.createDirectories(classes);
    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    StringWriter errors = new StringWriter();
    try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, null)) {
      boolean compiled =
          javac
              .getTask(
                  errors,
                  files,
                  null,
                  List.of("-d", classes.toString(), "-proc:none"),
                  null,
                  files.getJavaFileObjectsFromStrings(sources))
              .call();
      if (!compiled) {
        return "COMPILE ERROR: " + errors;
      }
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream saved = System.out;
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
      try {
        loader
            .loadClass("p.Main")
            .getMethod("main", String[].class)
            .invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException e) {
        System.out.println("THREW " + e.getCause());
      }
    } finally {
      System.setOut(saved);
    }
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static Properties properties(Path file) throws IOException {
    Properties p = new Properties();
    try (Reader reader = Files.newBufferedReader(file)) {
      p.load(reader);
    }
    return p;
  }
}
