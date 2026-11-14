package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  /** What a deferred body returned, and the stubs its end check declared. */
  record Checked<T>(T result, Stubs.Declared declared) {}

  /**
   * Runs {@code body} deferred, then checks the project once, as a batch with check "end" does: the
   * stubs it needs are declared, and when it still adds compile errors, its files are restored and
   * {@code what} is refused with {@code fix}. Otherwise its writes are journaled.
   */
  static <T> Checked<T> checkedAtEnd(
      SourceProject project, java.util.concurrent.Callable<T> body, String what, String fix)
      throws Exception {
    Map<String, Integer> before = TypeCheck.errors(project);
    T result = deferred(project, body);
    Map<Path, String> old = Journal.written(project);
    Stubs.Check declaring;
    try {
      declaring = Stubs.declare(project, before, old);
    } catch (IOException | RuntimeException e) {
      Journal.restore(
          project, old); // the stubs it wrote too, which the call's journal does not hold
      throw e;
    }
    if (!declaring.introduced().isEmpty()) {
      Journal.restore(project, old);
      throw refusal(project, declaring.introduced(), what, fix);
    }
    journal(project, old);
    return new Checked<>(result, declaring.declared());
  }

  /**
   * The refusal for an edit that adds {@code introduced} errors: "{@code what} would add compile
   * errors, so nothing was changed", the errors, a note when Maven gave no classpath, then {@code
   * fix}; blank final fields left unset name the {@code constructor} that sets them, and methods
   * that must override in a type with no such supertype name {@code add_supertype}, instead of
   * {@code fix}, or before it when other errors come with them.
   */
  private static Refusal refusal(
      SourceProject project, List<String> introduced, String what, String fix) {
    boolean unresolved = introduced.stream().anyMatch(e -> e.contains("cannot be resolved"));
    String missing =
        unresolved && project.classpathProblem() != null
            ? "\nMaven gave no classpath, so library types do not resolve: "
                + project.classpathProblem()
            : "";
    List<String> unset = new ArrayList<>();
    Map<String, List<String>> overriding = new LinkedHashMap<>();
    for (String error : introduced) {
      Matcher m = BLANK_FINAL.matcher(error);
      if (m.find() && !unset.contains(m.group(1))) {
        unset.add(m.group(1));
      }
      Matcher o = NO_SUPERTYPE.matcher(error);
      if (o.find()) {
        overriding.computeIfAbsent(o.group(2), t -> new ArrayList<>()).add(o.group(1));
      }
    }
    List<String> hints = new ArrayList<>();
    if (!unset.isEmpty()) {
      hints.add(
          "A constructor sets final fields: add_declaration with constructor: [\""
              + String.join("\", \"", unset)
              + "\"] writes it; send it with the fields, in one batch.");
    }
    overriding.forEach((type, methods) ->
        hints.add(
            type
                + " has no supertype declaring "
                + String.join(", ", methods)
                + ": give it one first, add_supertype with name "
                + type
                + " and relation implements (an interface) or extends (a class), then the"
                + " methods, in one batch."));
    String next = fix;
    if (!hints.isEmpty()) {
      boolean only =
          introduced.stream()
              .allMatch(e -> BLANK_FINAL.matcher(e).find() || NO_SUPERTYPE.matcher(e).find());
      next = String.join("\n", hints) + (only ? "" : "\n" + fix);
    }
    return new Refusal(
        what
            + " would add compile errors, so nothing was changed:\n  "
            + String.join("\n  ", introduced)
            + missing
            + "\n"
            + next);
  }

  /** A compile error for a final field no constructor sets; its group 1 is the field's name. */
  private static final Pattern BLANK_FINAL =
      Pattern.compile("The blank final field (\\w+) may not have been initialized");

  /**
   * A compile error for a method that must override but its type has no supertype declaring it;
   * group 1 is the method, group 2 the type.
   */
  private static final Pattern NO_SUPERTYPE =
      Pattern.compile(
          "The method (\\S+\\(.*?\\)) of type (\\w+) must override or implement a supertype method");

  /**
   * Writes each file's new text; a null text deletes the file. Returns the compile errors the edit
   * would add, and the stubs it declared; when there are any errors, nothing is left changed.
   */
  static Stubs.Check apply(SourceProject project, Map<Path, String> texts) throws IOException {
    if (project.session().deferred) {
      for (Map.Entry<Path, String> change : texts.entrySet()) {
        String old = Files.exists(change.getKey()) ? Files.readString(change.getKey()) : null;
        Journal.put(project, change.getKey(), change.getValue());
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
        Journal.put(project, change.getKey(), change.getValue());
      }
      Stubs.Check check = Stubs.declare(project, before, old);
      if (!check.introduced().isEmpty()) {
        Journal.restore(project, old);
      } else {
        journal(project, old);
      }
      return check;
    } catch (IOException | RuntimeException e) {
      Journal.restore(project, old);
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
      throw refusal(project, check.introduced(), what, fix);
    }
    return check.declared();
  }

  /** Writes a file that is not Java, which the gate does not check, creating its directories. */
  static void write(SourceProject project, Path file, String text) throws IOException {
    String before = Files.exists(file) ? Files.readString(file) : null;
    String mode = Journal.modeOf(file);
    Journal.put(project, file, text);
    Journal.wrote(project, file, before, text);
    Journal.mode(project, file, mode);
  }

  /** Records each file in {@code old} as written: from its old text to what it holds now. */
  static void journal(SourceProject project, Map<Path, String> old) throws IOException {
    for (Map.Entry<Path, String> file : old.entrySet()) {
      Path f = file.getKey();
      Journal.wrote(project, f, file.getValue(), Files.exists(f) ? Files.readString(f) : null);
    }
  }
}
