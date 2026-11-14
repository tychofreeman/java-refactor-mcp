package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

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
      String supertypes =
          declaration == null ? "" : addSupertypes(content, Files.readString(path), rel);
      throw new Refusal(
          rel
              + " already exists. Set overwrite to replace all of it, or change part of it with"
              + " replace_text, add_declaration or replace_declaration."
              + supertypes);
    }
    if (rel.endsWith(".java")) {
      CompilationUnit unit = parses(content, rel);
      String expected = project.packageOf(path);
      String declared = Names.packageOf(unit);
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
            "Declare what it uses first (add_declaration or create_file), then create it; or send"
                + " it in a batch with check \"end\", the steps that declare them after it.");
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

  /**
   * For a declaration whose file exists: the add_supertype calls that give the file's type the
   * supertypes {@code declared} names and it lacks, so its members are kept; "" when none are
   * missing.
   */
  private static String addSupertypes(String declared, String existing, String rel) {
    List<String> calls = new ArrayList<>();
    for (Object o : parses(declared, rel).types()) {
      if (!(o instanceof TypeDeclaration t)) {
        continue;
      }
      String name = t.getName().getIdentifier();
      Set<String> has = new HashSet<>();
      for (Object e : parses(existing, rel).types()) {
        if (e instanceof TypeDeclaration h && h.getName().getIdentifier().equals(name)) {
          if (h.getSuperclassType() != null) {
            has.add(h.getSuperclassType().toString());
          }
          for (Object i : h.superInterfaceTypes()) {
            has.add(i.toString());
          }
        }
      }
      String call = "add_supertype with name " + name + ", relation ";
      if (t.getSuperclassType() != null && !has.contains(t.getSuperclassType().toString())) {
        calls.add(call + "extends, supertype " + t.getSuperclassType());
      }
      for (Object i : t.superInterfaceTypes()) {
        if (!has.contains(i.toString())) {
          calls.add(call + (t.isInterface() ? "extends" : "implements") + ", supertype " + i);
        }
      }
    }
    return calls.isEmpty()
        ? ""
        : " To keep its members and give it the declaration's supertypes, send "
            + String.join(", then ", calls)
            + ".";
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
                    + " of the file."),
        Precondition.that(
            () -> old == null || !old.isEmpty(),
            () ->
                "old is empty: give the text to replace, or append=true to add new at the end of"
                    + " the file."));
    String replacement = args.string("new");
    Path path = project.existingFile(rel);
    String text = Files.readString(path);
    if (append) {
      return append(project, path, rel, text, replacement, args);
    }

    String match = old;
    String put = replacement;
    String shifted = "";
    List<Integer> at = new ArrayList<>();
    for (int i = text.indexOf(old); i >= 0; i = text.indexOf(old, i + old.length())) {
      // An old that starts with spaces, found inside a line's indentation, is not exact: the
      // loose match below takes the whole indentation, and new moves with it.
      if (!(old.startsWith(" ") && IndentShift.insideIndent(text, i))) {
        at.add(i);
      }
    }
    if (at.isEmpty()) {
      List<IndentShift> loose = IndentShift.locate(text, old);
      if (loose.isEmpty()) {
        throw new Refusal(
            "old is not in "
                + rel
                + ", though spacing within lines may differ"
                + IndentShift.hint(text, old)
                + ". Copy it from the file (from inspect, without the line numbers).");
      }
      if (loose.size() > 1) {
        List<Integer> lines = new ArrayList<>();
        for (IndentShift s : loose) {
          lines.add(Lines.number(text, s.start()));
        }
        throw new Refusal(
            "old occurs "
                + loose.size()
                + " times in "
                + rel
                + " with the spacing within lines ignored ("
                + Lines.list(lines)
                + "). Make old longer so it occurs once.");
      }
      IndentShift s = loose.get(0);
      match = text.substring(s.start(), s.end());
      put = s.by() == 0 ? replacement : IndentShift.shift(replacement, s.by(), s.whole());
      at.add(s.start());
      int n = Math.abs(s.by());
      shifted =
          "; old matched with the spacing within lines ignored"
              + (n == 0
                  ? ""
                  : ", and new was moved "
                      + n
                      + (s.by() < 0 ? " spaces shallower" : " spaces deeper")
                      + " to the file's indentation");
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
      out.append(put);
      int last = put.isEmpty() ? first : Lines.number(out, Math.max(out.length() - 1, 0));
      spans.add(new int[] {first, last});
      from = i + match.length();
    }
    out.append(text, from, text.length());
    if (project.session().skeletonOnly && rel.endsWith(".java")) {
      Generated.checkChanged(text, out.toString(), rel, "replace_text");
    }

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
                shifted + fixed.said()),
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
    String rel = project.relative(path);
    if (rel.endsWith(".java")) {
      resultParses(project, after, rel, what);
    }
    Imports.Fixed fixed =
        project.packageOf(path) == null
            ? new Imports.Fixed(after, 0, List.of())
            : FromFields.complete(before, Imports.fix(project, path, before, after, chosen), rel);
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
    return gated(project, path, before, text, chosen, what, fix);
  }

  /** {@code source} parsed, or refused with its first syntax errors and their lines. */
  static CompilationUnit parses(String source, String what) {
    CompilationUnit unit = Jdt.parseSyntax(source);
    String errors = syntaxErrors(unit);
    if (!errors.isEmpty()) {
      throw new Refusal(what + " does not parse:" + errors + "\nFix the syntax and send it again.");
    }
    return unit;
  }

  /**
   * The first three syntax errors of {@code unit}, each on a line of its own after its line number.
   */
  private static String syntaxErrors(CompilationUnit unit) {
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
    return errors.toString();
  }

  /**
   * Refuses {@code text}, what {@code what} made of {@code rel}, when it does not parse: under an
   * edit, as syntax the model sent; under a refactoring, as code the tool itself wrote.
   */
  private static void resultParses(SourceProject project, String text, String rel, String what) {
    if (project.session().editing) {
      parses(text, "the result");
      return;
    }
    String errors = syntaxErrors(Jdt.parseSyntax(text));
    if (!errors.isEmpty()) {
      throw new Refusal(
          what
              + " wrote code that does not parse:"
              + errors
              + "\nThis is a fault in the tool, not in your code: "
              + rel
              + " is unchanged. Make this change with an edit instead.");
    }
  }

  /**
   * Files as written: each one's text with its imports fixed (null for a file deleted), each file's
   * import notes, as "{@code rel}: imported a.B", and the stubs the gate declared.
   */
  record WrittenFiles(Map<Path, String> texts, List<String> notes, Stubs.Declared declared) {

    /** "; a/B.java: imported c.D", or nothing. */
    String said() {
      return notes.isEmpty() ? "" : "; " + String.join("; ", notes);
    }
  }

  /**
   * Applies {@code edits} to each file's text, from {@code texts} or else read from disk, fixes
   * each changed file's imports ({@code chosen} the classes to import where a name is ambiguous),
   * and writes them all under the gate, refusing as {@link Gate#applyOrRefuse} does. A file with no
   * edits is left alone; a null list deletes the file.
   */
  static WrittenFiles gatedFiles(
      SourceProject project,
      Map<Path, String> texts,
      Map<Path, List<TextEdit>> edits,
      List<String> chosen,
      String what,
      String fix)
      throws IOException {
    Map<Path, String> changed = new LinkedHashMap<>();
    List<String> notes = new ArrayList<>();
    for (Map.Entry<Path, List<TextEdit>> e : edits.entrySet()) {
      Path file = e.getKey();
      if (e.getValue() == null) {
        changed.put(file, null);
        continue;
      }
      if (e.getValue().isEmpty()) {
        continue;
      }
      String before = textOf(texts, file);
      String after = TextEdit.apply(before, e.getValue(), 0);
      Imports.Fixed fixed = Imports.fix(project, file, before, after, chosen);
      changed.put(file, fixed.text());
      if (!fixed.notes().isEmpty()) {
        notes.add(project.relative(file) + ": " + String.join("; ", fixed.notes()));
      }
    }
    return new WrittenFiles(changed, notes, Gate.applyOrRefuse(project, changed, what, fix));
  }

  /** {@code file}'s text in {@code texts}, or else as on disk. */
  static String textOf(Map<Path, String> texts, Path file) throws IOException {
    return texts.containsKey(file) ? texts.get(file) : Files.readString(file);
  }

  /**
   * Writes a new type's file, {@code target}, its imports fixed ({@code chosen} for an ambiguous
   * name), through the gate; a refusal names it as creating {@code type} and ends with {@code fix}.
   */
  static void newFile(
      SourceProject project, Path target, String text, String type, List<String> chosen, String fix)
      throws Exception {
    String fixed = Imports.fix(project, target, "", text, chosen).text();
    Gate.applyOrRefuse(project, Map.of(target, fixed), "creating " + type, fix);
  }
}
