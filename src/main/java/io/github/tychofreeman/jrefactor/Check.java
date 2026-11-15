package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
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
    Run run(Path root, List<String> classes, int seconds) throws IOException;
  }

  /** The check tool: the project's compile errors, and with run_tests the session's tests. */
  static Outcome run(SourceProject project, Args args) throws Exception {
    Map<TypeCheck.Problem, Integer> errors = TypeCheck.errors(project);
    StringBuilder said = new StringBuilder(errors(errors));
    if (args.flag("run_tests") || !args.list("tests").isEmpty()) {
      Set<Path> changed = new LinkedHashSet<>();
      for (Journal.Entry entry : project.session().done) {
        changed.addAll(entry.after().keySet());
      }
      said.append("\n").append(tests(project, args, changed, "this session's changes").text());
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
    if (args.flag("run_tests") || !args.list("tests").isEmpty()) {
      Tests tests = tests(project, args, Journal.written(project).keySet(), "the batch's changes");
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

  /**
   * Runs the tests {@code args} names, or else the test classes that use the files {@code changed}
   * ({@code what} names them), for the seconds its {@code timeout} gives, {@link #TIMEOUT} if none.
   */
  private static Tests tests(
      SourceProject project, Args args, Collection<Path> changed, String what) throws IOException {
    Path root = project.root();
    if (!MavenBuild.isMaven(root)) {
      throw new Refusal(
          "check runs the tests through Maven, and the project has no pom.xml; check without"
              + " run_tests and tests reports the compile errors.");
    }
    Integer timeout = args.integer("timeout");
    if (timeout != null && timeout <= 0) {
      throw new Refusal(
          "timeout is the seconds the tests may run, more than 0; got " + timeout + ".");
    }
    List<String> named = args.list("tests");
    List<String> tests = named.isEmpty() ? focused(project, changed) : named;
    if (tests.isEmpty()) {
      return new Tests(
          "no test uses " + what + ", so no test ran; tests names the tests to run", null);
    }
    Run run = project.session().tests.run(root, tests, timeout == null ? TIMEOUT : timeout);
    String ran =
        String.join(", ", named.isEmpty() ? tests.stream().map(Names::simpleName).toList() : named);
    String which = named.isEmpty() ? " (those that use " + what + ")" : "";
    String failed = String.join("\n  ", run.failures().stream().map(Failure::text).toList());
    if (run.problem() != null) {
      return new Tests(
          "the tests of "
              + ran
              + which
              + " "
              + run.problem()
              + (failed.isEmpty() ? "" : "\nFailed before then:\n  " + failed),
          run);
    }
    if (!failed.isEmpty()) {
      return new Tests(
          Names.plural(run.failures().size(), "test")
              + " of "
              + run.tests()
              + " failed, in "
              + ran
              + which
              + ":\n  "
              + failed,
          run);
    }
    if (run.tests() == 0) {
      return new Tests("no test of " + ran + " ran; tests takes classes or surefire patterns", run);
    }
    return new Tests(Names.plural(run.tests(), "test") + " of " + ran + which + " passed", run);
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

  /** The seconds tests may run when check is given no timeout. */
  static final int TIMEOUT = 30;

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
   * Runs {@code tests} (classes or surefire patterns) through Maven ({@code test -Dtest=...}) in
   * {@code root}, stopping it after {@code seconds}, and reads the reports surefire wrote.
   */
  static Run maven(Path root, List<String> tests, int seconds) throws IOException {
    Path log = Files.createTempFile("jrefactor-test", ".log");
    try {
      List<String> command = new ArrayList<>(MavenBuild.maven(root));
      command.addAll(
          List.of(
              "-B",
              "test",
              "-Dtest=" + String.join(",", tests),
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
      boolean finished;
      try {
        finished = process.waitFor(seconds, TimeUnit.SECONDS);
        if (!finished) {
          process.descendants().forEach(ProcessHandle::destroyForcibly);
          process.destroyForcibly();
          process.waitFor();
        }
      } catch (InterruptedException e) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new IOException("interrupted while Maven ran the tests", e);
      }
      int count = 0;
      List<Failure> failures = new ArrayList<>();
      Path reports = root.resolve("target/surefire-reports");
      if (Files.isDirectory(reports)) {
        try (Stream<Path> files = Files.list(reports)) {
          for (Path report : files.sorted().toList()) {
            String name = report.getFileName().toString();
            if (name.startsWith("TEST-")
                && name.endsWith(".xml")
                && Files.getLastModifiedTime(report).compareTo(start) >= 0) {
              Run one = report(Files.readString(report));
              count += one.tests();
              failures.addAll(one.failures());
            }
          }
        }
      }
      String problem =
          !finished
              ? "ran past "
                  + seconds
                  + " s, and Maven was stopped; pass timeout with more seconds to wait longer"
              : process.exitValue() != 0 && failures.isEmpty()
                  ? "could not run: Maven failed (exit "
                      + process.exitValue()
                      + "): "
                      + String.join(" ", command)
                      + "\n"
                      + MavenBuild.tail(log)
                  : null;
      return new Run(count, failures, problem);
    } finally {
      Files.deleteIfExists(log);
    }
  }

  /**
   * The test classes, by qualified name, that use the files {@code changed}: each Java file changed
   * under {@code src/test/java}, and each file there whose code refers to a type a changed file of
   * {@code src/main/java} declares, or to a member of one (by binding, so a name in a comment does
   * not count). In the order of their paths.
   */
  static List<String> focused(SourceProject project, Collection<Path> changed) throws IOException {
    Path main = project.root().resolve("src/main/java");
    Path test = project.root().resolve("src/test/java");
    Set<Path> tests = new TreeSet<>();
    Set<String> types = new HashSet<>();
    for (Path file : changed) {
      String name = file.getFileName().toString();
      if (!name.endsWith(".java") || name.equals("module-info.java")) {
        continue;
      }
      if (file.startsWith(test) && Files.isRegularFile(file)) {
        tests.add(file);
      } else if (file.startsWith(main)) {
        String rel = main.relativize(file).toString();
        types.add(
            rel.substring(0, rel.length() - ".java".length()).replace(File.separatorChar, '.'));
        if (Files.isRegularFile(file)) {
          CompilationUnit unit = Jdt.parseSyntax(Files.readString(file));
          String pkg = Names.packageOf(unit);
          for (Object type : unit.types()) {
            String simple = ((AbstractTypeDeclaration) type).getName().getIdentifier();
            types.add(pkg.isEmpty() ? simple : pkg + "." + simple);
          }
        }
      }
    }
    if (!types.isEmpty() && Files.isDirectory(test)) {
      Set<String> simple = types.stream().map(Names::simpleName).collect(Collectors.toSet());
      Holding holding =
          Holding.where(
              project,
              (file, text) ->
                  file.startsWith(test)
                      && !tests.contains(file)
                      && simple.stream().anyMatch(text::contains));
      holding
          .units()
          .forEach((file, unit) -> {
            if (uses(unit, types)) {
              tests.add(file);
            }
          });
    }
    return tests.stream()
        .map(f -> test.relativize(f).toString())
        .map(r -> r.substring(0, r.length() - ".java".length()).replace(File.separatorChar, '.'))
        .toList();
  }

  /**
   * Whether {@code unit} refers to a type whose top-level type is one of {@code types} (qualified
   * names), or to a method or field of one.
   */
  private static boolean uses(CompilationUnit unit, Set<String> types) {
    for (SimpleName name : Nodes.all(unit, SimpleName.class)) {
      ITypeBinding owner =
          switch (name.resolveBinding()) {
            case ITypeBinding t -> t;
            case IMethodBinding m -> m.getDeclaringClass();
            case IVariableBinding v when v.isField() -> v.getDeclaringClass();
            case null, default -> null;
          };
      if (owner != null && owner.isArray()) {
        owner = owner.getElementType();
      }
      if (owner != null
          && !owner.isPrimitive()
          && !owner.isTypeVariable()
          && types.contains(Names.outermost(owner).getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}
