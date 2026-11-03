package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies an edit under the type-check gate: writes the new texts, type-checks the project, and
 * restores every file (removing the new ones) if the edit adds compile errors.
 */
final class Gate {

  private Gate() {}

  /** Runs {@code body} with the type check deferred, as a batch with check "end" does. */
  static <T> T deferred(SourceProject project, java.util.concurrent.Callable<T> body)
      throws Exception {
    boolean outer = project.session().deferred;
    project.session().deferred = true;
    try {
      return body.call();
    } finally {
      project.session().deferred = outer;
    }
  }

  /**
   * Writes each file's new text; a null text deletes the file. Returns the compile errors the edit
   * would add, and the stubs it declared; when there are any errors, nothing is left changed.
   */
  static Stubs.Check apply(SourceProject project, Map<Path, String> texts) throws IOException {
    if (project.session().deferred) {
      for (Map.Entry<Path, String> change : texts.entrySet()) {
        String old = Files.exists(change.getKey()) ? Files.readString(change.getKey()) : null;
        put(change.getKey(), change.getValue());
        Journal.wrote(project, change.getKey(), old, change.getValue());
      }
      return new Stubs.Check(List.of(), Stubs.Declared.NONE);
    }
    Map<String, Integer> before = TypeCheck.errors(project);
    Map<Path, String> old = new LinkedHashMap<>();
    for (Path file : texts.keySet()) {
      old.put(file, Files.exists(file) ? Files.readString(file) : null);
    }
    try {
      for (Map.Entry<Path, String> change : texts.entrySet()) {
        put(change.getKey(), change.getValue());
      }
      Stubs.Check check = Stubs.declare(project, before, old);
      if (!check.introduced().isEmpty()) {
        restore(old);
      } else {
        journal(project, old);
      }
      return check;
    } catch (IOException | RuntimeException e) {
      restore(old);
      throw e;
    }
  }

  /**
   * {@link #apply}, refusing when the edit adds compile errors: "{@code what} would add compile
   * errors, so nothing was changed", the errors, then {@code fix}. Returns the stubs declared.
   */
  static Stubs.Declared applyOrRefuse(
      SourceProject project, Map<Path, String> texts, String what, String fix) throws IOException {
    Stubs.Check check = apply(project, texts);
    if (!check.introduced().isEmpty()) {
      boolean unresolved =
          check.introduced().stream().anyMatch(e -> e.contains("cannot be resolved"));
      String missing =
          unresolved && project.classpathProblem() != null
              ? "\nMaven gave no classpath, so library types do not resolve: "
                  + project.classpathProblem()
              : "";
      throw new Refusal(
          what
              + " would add compile errors, so nothing was changed:\n  "
              + String.join("\n  ", check.introduced())
              + missing
              + "\n"
              + fix);
    }
    return check.declared();
  }

  /** Writes a file that is not Java, which the gate does not check, creating its directories. */
  static void write(SourceProject project, Path file, String text) throws IOException {
    String before = Files.exists(file) ? Files.readString(file) : null;
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    Journal.wrote(project, file, before, text);
  }

  /** Writes {@code text} to {@code file}, creating its directories; null deletes the file. */
  private static void put(Path file, String text) throws IOException {
    if (text == null) {
      Files.deleteIfExists(file);
    } else {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
    }
  }

  static void restore(Map<Path, String> old) throws IOException {
    for (Map.Entry<Path, String> file : old.entrySet()) {
      if (file.getValue() == null) {
        Files.deleteIfExists(file.getKey());
      } else {
        Files.writeString(file.getKey(), file.getValue());
      }
    }
  }

  /** Records each file in {@code old} as written: from its old text to what it holds now. */
  static void journal(SourceProject project, Map<Path, String> old) throws IOException {
    for (Map.Entry<Path, String> file : old.entrySet()) {
      Path f = file.getKey();
      Journal.wrote(project, f, file.getValue(), Files.exists(f) ? Files.readString(f) : null);
    }
  }
}
