package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Where the project stands: its compile errors, and the tests the changes reach. The {@code check}
 * tool reports it; a {@code check} step of a batch requires it, as a checkpoint.
 */
final class Check {

  private Check() {}

  /** How many compile errors a report lists before it says how many more there are. */
  private static final int SHOWN = 10;

  /** A test that failed: the test's class and method, the file and line, the first message line. */
  record Failure(String test, String where, String message) {
    String text() {
      return test + (where.isEmpty() ? "" : " (" + where + ")") + ": " + message;
    }
  }

  /**
   * A run of tests: how many ran, those that failed, and, when Maven failed with no test to blame
   * (a compile error, a plugin it could not get), its last lines of output; else null.
   */
  record Run(int tests, List<Failure> failures, String problem) {
    boolean passed() {
      return failures.isEmpty() && problem == null;
    }
  }

  /** Runs the named test classes of the project at a root. */
  interface Runner {
    Run run(Path root, List<String> classes) throws IOException;
  }

  /** The check tool: the project's compile errors, and with run_tests the session's tests. */
  static Outcome run(SourceProject project, Args args) throws Exception {
    Map<TypeCheck.Problem, Integer> errors = TypeCheck.errors(project);
    StringBuilder said = new StringBuilder(errors(errors));
    if (args.flag("run_tests")) {
      Set<Path> changed = new LinkedHashSet<>();
      for (Journal.Entry entry : project.session().done) {
        changed.addAll(entry.after().keySet());
      }
      said.append("\n").append(tests(project, changed, "this session's changes").text());
    }
    return Outcome.of(said.toString());
  }

  /**
   * A check step of a batch, after the steps before it: with {@code baseline} (a batch checked at
   * its end), the stubs earlier steps need are declared and the project may have no compile errors
   * beyond it; with run_tests, the tests the batch's changes reach must pass. Refused otherwise.
   */
  static String checkpoint(
      SourceProject project, Map<String, Object> values, Map<TypeCheck.Problem, Integer> baseline)
      throws Exception {
    Args args = new Args("check", values, Tools.CHECK.arguments());
    args.check(Tools.CHECK.arguments());
    String said = "type-checks";
    if (baseline != null) {
      Map<Path, String> old = Journal.written(project);
      Stubs.Check check = Stubs.declare(project, baseline, old);
      Gate.journal(project, old);
      if (!check.introduced().isEmpty()) {
        throw new Refusal(
            "the code does not type-check at this checkpoint:\n  "
                + String.join("\n  ", check.introduced().stream().map(Gate::text).toList())
                + "\nAdd the steps that fix them before the check, or move the check after them.");
      }
      said += check.declared().text();
    }
    if (args.flag("run_tests")) {
      Tests tests = tests(project, Journal.written(project).keySet(), "the batch's changes");
      if (tests.run() != null && !tests.run().passed()) {
        throw new Refusal(
            tests.text() + "\nFix the code or the test in steps before the check, or check later.");
      }
      said += "; " + tests.text();
    }
    return "check: " + said;
  }

  /** The tests a change reached, as a report says them, and their run, if any ran. */
  private record Tests(String text, Run run) {}

  /** Runs the test classes that {@code changed} reaches; {@code what} names the changes. */
  private static Tests tests(SourceProject project, Collection<Path> changed, String what)
      throws IOException {
    Path root = project.root();
    if (!MavenBuild.isMaven(root)) {
      throw new Refusal(
          "run_tests runs the tests through Maven, and the project has no pom.xml; check without"
              + " run_tests reports the compile errors.");
    }
    List<String> classes = focused(root, changed);
    if (classes.isEmpty()) {
      return new Tests("no test class reaches " + what + ", so no test ran", null);
    }
    Run run = project.session().tests.run(root, classes);
    String ran = String.join(", ", classes.stream().map(Names::simpleName).toList());
    if (run.problem() != null) {
      return new Tests("Maven failed running the tests of " + ran + ":\n" + run.problem(), run);
    }
    if (run.failures().isEmpty()) {
      return new Tests(
          Names.plural(run.tests(), "test") + " of " + ran + " passed (those " + what + " reach)",
          run);
    }
    return new Tests(
        Names.plural(run.failures().size(), "test")
            + " of "
            + run.tests()
            + " failed, in "
            + ran
            + ":\n  "
            + String.join("\n  ", run.failures().stream().map(Failure::text).toList()),
        run);
  }

  /**
   * The test classes, by qualified name, that {@code changed} reaches: each Java file changed under
   * {@code src/test/java}, and each file there that names a type a changed main file declares (by
   * the simple name of its file, a word of the test's text). In the order of their paths.
   */
  static List<String> focused(Path root, Collection<Path> changed) throws IOException {
    Path main = root.resolve("src/main/java");
    Path test = root.resolve("src/test/java");
    Set<Path> tests = new LinkedHashSet<>();
    List<Pattern> names = new ArrayList<>();
    for (Path file : changed) {
      String name = file.getFileName().toString();
      if (!name.endsWith(".java") || name.equals("module-info.java")) {
        continue;
      }
      if (file.startsWith(test) && Files.isRegularFile(file)) {
        tests.add(file);
      } else if (file.startsWith(main)) {
        String type = name.substring(0, name.length() - ".java".length());
        names.add(Pattern.compile("\\b" + Pattern.quote(type) + "\\b"));
      }
    }
    if (!names.isEmpty() && Files.isDirectory(test)) {
      try (Stream<Path> files = Files.walk(test)) {
        for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
          String text = Files.readString(file);
          if (names.stream().anyMatch(n -> n.matcher(text).find())) {
            tests.add(file);
          }
        }
      }
    }
    return tests.stream()
        .sorted()
        .map(f -> test.relativize(f).toString())
        .map(r -> r.substring(0, r.length() - ".java".length()).replace(File.separatorChar, '.'))
        .toList();
  }

  /** The compile errors as a report says them: how many, then the first few. */
  private static String errors(Map<TypeCheck.Problem, Integer> errors) {
    if (errors.isEmpty()) {
      return "type-checks: no compile errors";
    }
    int count = errors.values().stream().mapToInt(Integer::intValue).sum();
    List<String> lines =
        errors.entrySet().stream()
            .limit(SHOWN)
            .map(e ->
                e.getKey().rel()
                    + " | "
                    + e.getKey().message()
                    + (e.getValue() > 1 ? " (x" + e.getValue() + ")" : ""))
            .toList();
    return Names.plural(count, "compile error")
        + ":\n  "
        + String.join("\n  ", lines)
        + (errors.size() > SHOWN ? "\n  and " + (errors.size() - SHOWN) + " more" : "");
  }

  /** A frame of a stack trace: its class and method, then its file and line. */
  private static final Pattern FRAME = Pattern.compile("at ([\\w.$]+)\\.[\\w$<>]+\\(([^)]*)\\)");

  /**
   * The failed tests a surefire report ({@code TEST-*.xml}) lists, and how many tests it ran. A
   * failure's place is the first frame of its trace in the test's own class.
   */
  static Run report(String xml) throws IOException {
    Element suite;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      suite =
          factory
              .newDocumentBuilder()
              .parse(new InputSource(new StringReader(xml)))
              .getDocumentElement();
    } catch (Exception e) {
      throw new IOException("a surefire report that does not parse: " + e.getMessage(), e);
    }
    List<Failure> failures = new ArrayList<>();
    NodeList cases = suite.getElementsByTagName("testcase");
    for (int i = 0; i < cases.getLength(); i++) {
      Element testcase = (Element) cases.item(i);
      for (Node child = testcase.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof Element e
            && (e.getTagName().equals("failure") || e.getTagName().equals("error"))) {
          String owner = testcase.getAttribute("classname");
          failures.add(
              new Failure(
                  owner + "." + testcase.getAttribute("name"),
                  place(e.getTextContent(), owner),
                  firstLine(e.getAttribute("message"), e.getAttribute("type"))));
          break;
        }
      }
    }
    return new Run(cases.getLength(), failures, null);
  }

  /** The file and line of the first frame of {@code trace} in {@code owner}; empty if none. */
  private static String place(String trace, String owner) {
    Matcher frame = FRAME.matcher(trace);
    while (frame.find()) {
      if (frame.group(1).equals(owner)) {
        return frame.group(2);
      }
    }
    return "";
  }

  /** The first line of a failure's message, or its exception's type when it has none. */
  private static String firstLine(String message, String type) {
    String text = message.isBlank() ? type : message.strip();
    int end = text.indexOf('\n');
    return end < 0 ? text : text.substring(0, end);
  }

  /**
   * Runs {@code classes} through Maven ({@code test -Dtest=...}) in {@code root}, for as long as it
   * takes, and reads the reports surefire wrote for them.
   */
  static Run maven(Path root, List<String> classes) throws IOException {
    Path log = Files.createTempFile("jrefactor-test", ".log");
    try {
      List<String> command = new ArrayList<>(MavenBuild.maven(root));
      command.addAll(
          List.of(
              "-B",
              "test",
              "-Dtest=" + String.join(",", classes),
              "-Dsurefire.failIfNoSpecifiedTests=false",
              "-DfailIfNoTests=false"));
      FileTime start = FileTime.fromMillis(System.currentTimeMillis() - 1000);
      Process process =
          new ProcessBuilder(command)
              .directory(root.toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
              .start();
      try {
        process.waitFor();
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new IOException("interrupted while Maven ran the tests", e);
      }
      int tests = 0;
      List<Failure> failures = new ArrayList<>();
      Path reports = root.resolve("target/surefire-reports");
      for (String name : classes) {
        Path report = reports.resolve("TEST-" + name + ".xml");
        if (Files.isRegularFile(report)
            && Files.getLastModifiedTime(report).compareTo(start) >= 0) {
          Run one = report(Files.readString(report));
          tests += one.tests();
          failures.addAll(one.failures());
        }
      }
      String problem =
          process.exitValue() != 0 && failures.isEmpty()
              ? String.join(" ", command)
                  + " failed (exit "
                  + process.exitValue()
                  + "):\n"
                  + MavenBuild.tail(log)
              : null;
      return new Run(tests, failures, problem);
    } finally {
      Files.deleteIfExists(log);
    }
  }
}
