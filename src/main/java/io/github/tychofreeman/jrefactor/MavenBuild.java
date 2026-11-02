package io.github.tychofreeman.jrefactor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** What a Maven project's build says about its sources: its roots and its test classpath. */
final class MavenBuild {

  /**
   * The plugin that prints the classpath, pinned so a project's own plugin setup cannot vary it.
   */
  private static final String BUILD_CLASSPATH =
      "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath";

  private static final long TIMEOUT_SECONDS = 300;

  private MavenBuild() {}

  static boolean isMaven(Path root) {
    return Files.isRegularFile(root.resolve("pom.xml"));
  }

  /** {@code src/main/java} and {@code src/test/java}, those that exist. */
  static List<Path> sourceRoots(Path root) {
    List<Path> roots = new ArrayList<>();
    for (String dir : List.of("src/main/java", "src/test/java")) {
      if (Files.isDirectory(root.resolve(dir))) {
        roots.add(root.resolve(dir));
      }
    }
    return roots;
  }

  /**
   * The test-scope classpath, from Maven run in {@code root} (its {@code ./mvnw}, else {@code mvn}
   * on the PATH). Maven's output never reaches this process's stdout, which carries the protocol.
   *
   * @throws IOException with Maven's last lines of output when it cannot be run or fails
   */
  static List<String> classpath(Path root) throws IOException {
    Path out = Files.createTempFile("jrefactor-classpath", ".txt");
    Path log = Files.createTempFile("jrefactor-maven", ".log");
    try {
      List<String> command = new ArrayList<>(maven(root));
      command.addAll(
          List.of(
              "-q", "-B", BUILD_CLASSPATH, "-Dmdep.includeScope=test", "-Dmdep.outputFile=" + out));
      Process process =
          new ProcessBuilder(command)
              .directory(root.toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
              .start();
      boolean finished;
      try {
        finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw new IOException("interrupted while Maven printed the classpath", e);
      }
      if (!finished) {
        process.destroyForcibly();
        throw new IOException("Maven did not print the classpath in " + TIMEOUT_SECONDS + " s");
      }
      if (process.exitValue() != 0) {
        throw new IOException(
            String.join(" ", command)
                + " failed (exit "
                + process.exitValue()
                + "):\n"
                + tail(log));
      }
      String text = Files.readString(out).strip();
      return text.isEmpty() ? List.of() : Arrays.asList(text.split(File.pathSeparator));
    } finally {
      Files.deleteIfExists(out);
      Files.deleteIfExists(log);
    }
  }

  private static List<String> maven(Path root) throws IOException {
    Path wrapper = root.resolve("mvnw");
    if (Files.isExecutable(wrapper)) {
      return List.of(wrapper.toString());
    }
    for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "mvn"))) {
        return List.of(Path.of(dir, "mvn").toString());
      }
    }
    throw new IOException("no ./mvnw in the project and no mvn on the PATH");
  }

  private static String tail(Path log) throws IOException {
    List<String> lines = Files.readAllLines(log);
    return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
  }
}
