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

  /**
   * Writes each file's new text. Returns the compile errors the edit would add; when there are any,
   * nothing is left changed.
   */
  static List<String> apply(SourceProject project, Map<Path, String> texts) throws IOException {
    Map<String, Integer> before = TypeCheck.errors(project);
    Map<Path, String> old = new LinkedHashMap<>();
    for (Path file : texts.keySet()) {
      old.put(file, Files.exists(file) ? Files.readString(file) : null);
    }
    try {
      for (Map.Entry<Path, String> change : texts.entrySet()) {
        Files.createDirectories(change.getKey().getParent());
        Files.writeString(change.getKey(), change.getValue());
      }
      List<String> introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
      if (!introduced.isEmpty()) {
        restore(old);
      }
      return introduced;
    } catch (IOException | RuntimeException e) {
      restore(old);
      throw e;
    }
  }

  /**
   * {@link #apply}, refusing when the edit adds compile errors: "{@code what} would add compile
   * errors, so nothing was changed", the errors, then {@code fix}.
   */
  static void applyOrRefuse(SourceProject project, Map<Path, String> texts, String what, String fix)
      throws IOException {
    List<String> introduced = apply(project, texts);
    if (!introduced.isEmpty()) {
      throw new Refusal(
          what
              + " would add compile errors, so nothing was changed:\n  "
              + String.join("\n  ", introduced)
              + "\n"
              + fix);
    }
  }

  private static void restore(Map<Path, String> old) throws IOException {
    for (Map.Entry<Path, String> file : old.entrySet()) {
      if (file.getValue() == null) {
        Files.deleteIfExists(file.getKey());
      } else {
        Files.writeString(file.getKey(), file.getValue());
      }
    }
  }
}
