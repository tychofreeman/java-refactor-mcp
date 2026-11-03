package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.CompilationUnit;

/** Edits: changes of behavior on purpose, held to the same type-check gate as refactorings. */
final class Edits {

  private Edits() {}

  static Tools.Outcome createFile(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String content = args.required("content");
    Path path = project.file(rel);
    boolean exists = Files.exists(path);
    Stubs.Declared stubs = Stubs.Declared.NONE;
    if (exists && !args.flag("overwrite")) {
      throw new Refusal(
          rel
              + " already exists. Set overwrite to replace all of it, or change part of it with"
              + " replace_text, add_declaration or replace_declaration.");
    }
    if (rel.endsWith(".java")) {
      CompilationUnit unit = parses(content, rel);
      String expected = project.packageOf(path);
      String declared =
          unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
      if (expected != null && !expected.equals(declared)) {
        throw new Refusal(
            "content declares package "
                + (declared.isEmpty() ? "(none)" : declared)
                + ", but "
                + rel
                + " is in package "
                + (expected.isEmpty() ? "(the default package)" : expected)
                + ". Change the package line, or the file's path.");
      }
    }
    Written written =
        write(
            project,
            path,
            rel,
            exists ? Files.readString(path) : "",
            content,
            args.list("imports"),
            (exists ? "replacing " : "creating ") + rel,
            "Declare what it uses first (add_declaration or create_file), then create it.");
    Imports.Fixed fixed = written.fixed();
    stubs = written.declared();
    content = fixed.text();
    long lines = content.lines().count();
    return new Tools.Outcome(
        (exists ? "replaced " : "created ")
            + rel
            + " ("
            + lines
            + (lines == 1 ? " line)" : " lines)")
            + fixed.said(),
        stubs);
  }

  static Tools.Outcome replaceText(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String old = args.required("old");
    String replacement = args.string("new");
    if (replacement == null) {
      throw new Refusal("replace_text needs new (it may be empty, to delete old).");
    }
    Path path = project.existingFile(rel);
    String text = Files.readString(path);

    List<Integer> at = new ArrayList<>();
    for (int i = text.indexOf(old); i >= 0; i = text.indexOf(old, i + old.length())) {
      at.add(i);
    }
    if (at.isEmpty()) {
      throw new Refusal(
          "old is not in "
              + rel
              + ". Copy it from the file exactly (from inspect, without the line numbers).");
    }
    if (at.size() > 1 && !args.flag("all_occurrences")) {
      List<Integer> lines = new ArrayList<>();
      for (int i : at) {
        lines.add(Lines.number(text, i));
      }
      throw new Refusal(
          "old occurs "
              + at.size()
              + " times in "
              + rel
              + " ("
              + Lines.list(lines)
              + "). Make old longer so it occurs once, or set all_occurrences.");
    }

    StringBuilder out = new StringBuilder();
    List<int[]> spans = new ArrayList<>();
    int from = 0;
    for (int i : at) {
      out.append(text, from, i);
      int first = Lines.number(out, out.length());
      out.append(replacement);
      int last = replacement.isEmpty() ? first : Lines.number(out, Math.max(out.length() - 1, 0));
      spans.add(new int[] {first, last});
      from = i + old.length();
    }
    out.append(text, from, text.length());

    Written written =
        write(
            project,
            path,
            rel,
            text,
            out.toString(),
            args.list("imports"),
            "replacing old in " + rel,
            "Nothing was changed; adjust and retry.");
    Imports.Fixed fixed = written.fixed();
    List<String> ranges = new ArrayList<>();
    for (int[] span : spans) {
      int first = span[0] + fixed.lineShift();
      int last = span[1] + fixed.lineShift();
      ranges.add(first == last ? "" + first : first + "-" + last);
    }
    boolean one = ranges.size() == 1 && !ranges.get(0).contains("-");
    return new Tools.Outcome(
        "replaced %d occurrence%s in %s, %s %s%s"
            .formatted(
                at.size(),
                at.size() == 1 ? "" : "s",
                rel,
                one ? "line" : "lines",
                String.join(", ", ranges),
                fixed.said()),
        written.declared());
  }

  /** A file as written: its fixed text, and the stubs its gate declared. */
  record Written(Imports.Fixed fixed, Stubs.Declared declared) {}

  /**
   * Fixes the imports of {@code after}, which replaces {@code before} in {@code path} (when the
   * file is under a source root), and applies it under the gate, refusing as {@link
   * Gate#applyOrRefuse} does.
   */
  static Written gated(
      SourceProject project,
      Path path,
      String before,
      String after,
      List<String> chosen,
      String what,
      String fix)
      throws Exception {
    // Only a file under a source root has a package the imports can be resolved in; elsewhere
    // (a golden expected file, a script) the text is applied as written.
    Imports.Fixed fixed =
        project.packageOf(path) == null
            ? new Imports.Fixed(after, 0, List.of())
            : Imports.fix(project, path, before, after, chosen);
    Stubs.Declared declared = Gate.applyOrRefuse(project, Map.of(path, fixed.text()), what, fix);
    return new Written(fixed, declared);
  }

  /**
   * Writes a file, under the gate when it is Java, after fixing its imports against {@code before}.
   */
  private static Written write(
      SourceProject project,
      Path path,
      String rel,
      String before,
      String text,
      List<String> chosen,
      String what,
      String fix)
      throws Exception {
    if (!rel.endsWith(".java")) {
      Gate.write(project, path, text);
      return new Written(new Imports.Fixed(text, 0, List.of()), Stubs.Declared.NONE);
    }
    parses(text, "the result");
    return gated(project, path, before, text, chosen, what, fix);
  }

  /** {@code source} parsed, or refused with its first syntax errors and their lines. */
  static CompilationUnit parses(String source, String what) {
    CompilationUnit unit = Jdt.parseSyntax(source);
    StringBuilder errors = new StringBuilder();
    int count = 0;
    for (IProblem problem : unit.getProblems()) {
      if (problem.isError() && count++ < 3) {
        errors
            .append("\n  line ")
            .append(problem.getSourceLineNumber())
            .append(": ")
            .append(problem.getMessage());
      }
    }
    if (count > 0) {
      throw new Refusal(what + " does not parse:" + errors + "\nFix the syntax and send it again.");
    }
    return unit;
  }
}
