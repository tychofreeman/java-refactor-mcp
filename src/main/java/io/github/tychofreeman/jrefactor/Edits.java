package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;

/** Edits: changes of behavior on purpose, held to the same type-check gate as refactorings. */
final class Edits {

  private Edits() {}

  static Outcome createFile(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String content = args.string("content");
    String declaration = args.string("declaration");
    String testFor = args.string("test_for");
    int given =
        (content == null ? 0 : 1) + (declaration == null ? 0 : 1) + (testFor == null ? 0 : 1);
    if (given != 1) {
      throw new Refusal(
          given == 0
              ? "create_file needs content (or, for a .java file, declaration or test_for)."
              : "create_file takes one of content, declaration and test_for.");
    }
    Path path = project.file(rel);
    List<String> stubbed = List.of();
    String made = "";
    if (declaration != null || testFor != null) {
      String pkg = project.packageOf(path);
      if (!rel.endsWith(".java") || pkg == null) {
        throw new Refusal(
            (declaration != null ? "declaration" : "test_for")
                + " makes a .java file under a source root; give "
                + rel
                + " its content instead.");
      }
      if (declaration != null) {
        Skeleton.Made skeleton = Skeleton.of(project, path, pkg, declaration);
        content = skeleton.text();
        stubbed = skeleton.stubbed();
      } else {
        TestFor.Framework framework = TestFor.framework(project);
        TestFor.Target target = TestFor.target(project, testFor);
        String className = path.getFileName().toString().replaceFirst("\\.java$", "");
        content = TestFor.file(pkg, className, target, framework);
        made = target.method() == null ? "" : TestFor.note(target);
      }
    }
    if (project.session().skeletonOnly
        && declaration == null
        && testFor == null
        && rel.endsWith(".java")) {
      for (Object o : parses(content, rel).types()) {
        if (!((AbstractTypeDeclaration) o).bodyDeclarations().isEmpty()) {
          throw new Refusal(
              "skeleton-only creation is on: create_file writes a type's header, not its members."
                  + " Start the file with declaration (final class Cart implements Priced) or, for"
                  + " a test, test_for (Cart.total); then add members with add_declaration, where"
                  + " stubs and test_for give their shells.");
        }
      }
    }
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
    return new Outcome(
        (exists ? "replaced " : "created ")
            + rel
            + " ("
            + lines
            + (lines == 1 ? " line)" : " lines)")
            + fixed.said()
            + (stubbed.isEmpty()
                ? ""
                : "; stubs for what its supertypes leave abstract: " + String.join(", ", stubbed))
            + made,
        stubs);
  }

  static Outcome replaceText(SourceProject project, Args args) throws Exception {
    String rel = args.required("file");
    String old = args.string("old");
    boolean append = args.flag("append");
    Precondition.require(
        Precondition.that(
            () -> !append || (old == null && !args.flag("all_occurrences")),
            () -> "append adds new at the end of the file; leave out old and all_occurrences."),
        Precondition.that(
            () -> append || old != null,
            () ->
                "replace_text needs old, the text to replace, or append=true to add new at the end"
                    + " of the file."));
    String replacement = args.string("new");
    if (replacement == null) {
      throw new Refusal("replace_text needs new (it may be empty, to delete old).");
    }
    Path path = project.existingFile(rel);
    String text = Files.readString(path);
    if (append) {
      return append(project, path, rel, text, replacement, args);
    }

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
    return new Outcome(
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

  /**
   * replace_text with append: {@code added} after the end of {@code text}, a line break first when
   * the file does not end in one.
   */
  private static Outcome append(
      SourceProject project, Path path, String rel, String text, String added, Args args)
      throws Exception {
    String start = text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
    String after = start + added;
    int first = Lines.number(after, start.length());
    int last = added.isEmpty() ? first : Lines.number(after, after.length() - 1);
    Written written =
        write(
            project,
            path,
            rel,
            text,
            after,
            args.list("imports"),
            "appending to " + rel,
            "Nothing was changed; adjust and retry.");
    int shift = written.fixed().lineShift();
    int lines = last - first + 1;
    return new Outcome(
        "appended "
            + lines
            + (lines == 1 ? " line to " : " lines to ")
            + rel
            + ", "
            + (lines == 1
                ? "line " + (first + shift)
                : "lines " + (first + shift) + "-" + (last + shift))
            + written.fixed().said(),
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
