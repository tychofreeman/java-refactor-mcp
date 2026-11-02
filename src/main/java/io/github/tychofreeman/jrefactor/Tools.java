package io.github.tychofreeman.jrefactor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The server's tools: each one's name, description, schema and what a call does. */
final class Tools {

  private Tools() {}

  /** What a call does: its text, or a {@link Refusal}. */
  interface Handler {
    String run(SourceProject project, Args args) throws Exception;
  }

  /**
   * One tool. {@code args} are the argument names it takes; the schema leaves out {@code
   * "additionalProperties": false}, since with it the SDK refuses an unknown argument before the
   * tool can name the ones to use.
   */
  record Def(
      String name,
      String description,
      String schema,
      Set<String> args,
      boolean readOnly,
      Handler handler) {}

  static final Def INSPECT =
      new Def(
          "inspect",
          """
          Read Java code with line numbers. Use it before a refactoring to get exact lines.
          - file: the file with method, constructor and initializer bodies elided: declarations, \
          fields, signatures and comments in full, and in place of each body of two lines or more \
          the lines it takes.
          - file + start_line/end_line: those lines (end_line defaults to the end of the file).
          Paths are relative to the project root; lines are 1-based.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":       {"type": "string",  "description": "Java file path relative to the project root."},
              "start_line": {"type": "integer", "description": "First line to show (1-based)."},
              "end_line":   {"type": "integer", "description": "Last line to show (inclusive)."}
            },
            "required": ["file"]
          }\
          """,
          Set.of("file", "start_line", "end_line"),
          true,
          Tools::inspect);

  static final Def RENAME_LOCAL =
      new Def(
          "rename_local",
          """
          Rename a local variable or parameter and every use of it in the method that declares it.
          Identify it by file, line (its declaration or any use) and its current name; column only \
          when the name is on the line twice with different meanings.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused. Returns the lines changed.
          Refuses, saying why: a field (not built yet), a name that is not a variable, a new name \
          that is not a valid Java name or that another variable in the method already uses.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":     {"type": "string",  "description": "Java file path relative to the project root."},
              "line":     {"type": "integer", "description": "1-based line of the declaration or any use."},
              "name":     {"type": "string",  "description": "The variable's current name."},
              "column":   {"type": "integer", "description": "Optional 1-based column of the name, only to tell two meanings apart."},
              "new_name": {"type": "string",  "description": "The new name; a valid Java name."}
            },
            "required": ["file", "line", "name", "new_name"]
          }\
          """,
          Set.of("file", "line", "name", "column", "new_name"),
          false,
          Tools::renameLocal);

  static final Def CREATE_FILE =
      new Def(
          "create_file",
          """
          Create a new file, Java or not (a test, a fixture, a doc), with any missing directories: \
          an edit.
          A .java file must parse and declare the package its directory implies under a source \
          root, and the project must type-check no worse after: a file that uses what nothing \
          declares yet is refused, so add that first.
          The file must not exist, unless overwrite is set to replace all of it.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":      {"type": "string",  "description": "Path relative to the project root."},
              "content":   {"type": "string",  "description": "The whole file."},
              "overwrite": {"type": "boolean", "description": "Replace the file if it exists."}
            },
            "required": ["file", "content"]
          }\
          """,
          Set.of("file", "content", "overwrite"),
          false,
          (project, args) -> Edits.createFile(project, args));

  static final Def REPLACE_TEXT =
      new Def(
          "replace_text",
          """
          Replace exact text in a file: an edit, for what the other tools do not cover: string \
          literals, comments, a few lines of code, and files that are not Java (pom.xml, \
          fixtures, docs).
          old must be as in the file and occur once, or set all_occurrences. In a .java file the \
          project must type-check no worse after, or nothing changes. Imports the new text needs \
          are added and those it leaves unused removed; imports chooses an ambiguous name.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":            {"type": "string",  "description": "Path relative to the project root."},
              "old":             {"type": "string",  "description": "Text as it is in the file."},
              "new":             {"type": "string",  "description": "The text to put in its place."},
              "all_occurrences": {"type": "boolean", "description": "Replace every occurrence, not only one."},
              "imports":         {"type": "array", "items": {"type": "string"}, "description": "Optional: fully qualified names to import where a simple name is ambiguous."}
            },
            "required": ["file", "old", "new"]
          }\
          """,
          Set.of("file", "old", "new", "all_occurrences", "imports"),
          false,
          (project, args) -> Edits.replaceText(project, args));

  static final Def ADD_DECLARATION =
      new Def(
          "add_declaration",
          """
          Add members (methods, fields, constructors, nested types) to a type: an edit; for a new \
          file, use create_file. content is Java members with their Javadoc, written at any \
          indentation; they are indented to match.
          type names the type ("Cart", or "Cart.Line" for a nested one); without it, the file's \
          only top-level type. The members go at the end of the type, or just after or before \
          the member named in after or before.
          A method with the same name and parameter types whose body is only \
          throw new UnsupportedOperationException("not implemented") is a stub, and is replaced \
          rather than added again.
          Imports the members need are added, and those they no longer use are removed; when a \
          name could be several classes, the call is refused with them, and imports chooses.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":    {"type": "string", "description": "Java file path relative to the project root."},
              "content": {"type": "string", "description": "Java member declarations."},
              "type":    {"type": "string", "description": "Optional: the type to add to, as Outer or Outer.Inner."},
              "after":   {"type": "string", "description": "Optional: put them just after this member (a name)."},
              "before":  {"type": "string", "description": "Optional: put them just before this member."},
              "imports": {"type": "array", "items": {"type": "string"}, "description": "Optional: fully qualified names to import where a simple name is ambiguous."}
            },
            "required": ["file", "content"]
          }\
          """,
          Set.of("file", "content", "type", "after", "before", "imports"),
          false,
          (project, args) -> Declarations.add(project, args));

  static final Def REPLACE_DECLARATION =
      new Def(
          "replace_declaration",
          """
          Replace a whole declaration, found by name, with new source: an edit, for a signature \
          and body that change together, a field, or a type. Empty content deletes it, with its \
          Javadoc.
          name is Type, Type.member, or member (of the file's only top-level type); for one of \
          several overloads, add its parameter types: Cart.add(int, String).
          The old Javadoc stays unless content has its own. Callers a new signature or a \
          deletion breaks are refused.
          Imports content needs are added, and those only the old declaration used are \
          removed; when a name could be several classes, imports chooses.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":    {"type": "string", "description": "Java file path relative to the project root."},
              "name":    {"type": "string", "description": "Type, Type.member or member; with (parameter types) for an overload."},
              "content": {"type": "string", "description": "The new declaration; empty to delete it."},
              "imports": {"type": "array", "items": {"type": "string"}, "description": "Optional: fully qualified names to import where a simple name is ambiguous."}
            },
            "required": ["file", "name", "content"]
          }\
          """,
          Set.of("file", "name", "content", "imports"),
          false,
          (project, args) -> Declarations.replace(project, args));

  static final Def UNDO =
      new Def(
          "undo",
          """
          Undo the last change made through these tools in this session (one edit, refactoring \
          or whole batch), restoring each file it wrote. Call it again to step further back.
          Refuses when a file it would restore was changed since, by a tool or by hand.\
          """,
          """
          {
            "type": "object",
            "properties": {}
          }\
          """,
          Set.of(),
          false,
          (project, args) -> Journal.undo(project));

  static final Def BATCH =
      new Def(
          "batch",
          """
          Run several refactorings and edits in order as one change, planned from one read. Each \
          step is {"tool": ..., "args": {...}, "id": optional}: a tool and the arguments it takes \
          alone.
          Give line numbers as in the code you read before the batch: the batch moves them past \
          earlier steps. A line an earlier step rewrote cannot be given by number; name a line \
          an earlier step changed instead: "$prev.start", "$prev.end", "$2.end", "$id.start+1".
          If a step is refused, nothing changes and the result says which step and why. check \
          "end" type-checks once, after the last step, so edits may pass through code that does \
          not compile yet (a signature, then its callers). One undo reverts the whole batch.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "steps": {"type": "array", "description": "The steps, in order.", "items": {"type": "object", "properties": {"tool": {"type": "string"}, "args": {"type": "object"}, "id": {"type": "string"}}, "required": ["tool"]}},
              "check": {"type": "string", "enum": ["each", "end"], "description": "Type-check after each step (default), or once at the end."}
            },
            "required": ["steps"]
          }\
          """,
          Set.of("steps", "check"),
          false,
          (project, args) -> Batch.run(project, args));

  static final List<Def> ALL =
      List.of(
          INSPECT,
          RENAME_LOCAL,
          CREATE_FILE,
          REPLACE_TEXT,
          ADD_DECLARATION,
          REPLACE_DECLARATION,
          BATCH,
          UNDO);

  private static String inspect(SourceProject project, Args args) throws Exception {
    String file = args.required("file");
    Integer start = args.integer("start_line");
    Integer end = args.integer("end_line");
    if (start == null && end == null) {
      return Inspect.file(project, file);
    }
    return Inspect.lines(
        project, file, start == null ? 1 : start, end == null ? Integer.MAX_VALUE : end);
  }

  private static String renameLocal(SourceProject project, Args args) throws Exception {
    String file = args.required("file");
    String name = args.required("name");
    String newName = args.required("new_name");
    Integer line = args.integer("line");
    if (line == null) {
      throw new Refusal("rename_local needs line: the 1-based line of a use or declaration.");
    }
    Integer column = args.integer("column");
    RenameLocal.Result result =
        RenameLocal.run(project, file, line, name, column == null ? 0 : column, newName);
    return "renamed %s to %s in %s: %d occurrences, %s"
        .formatted(name, newName, result.file(), result.occurrences(), lines(result.lines()));
  }

  /** The tool named {@code name}, or a refusal listing the tools there are. */
  static Def named(String name) {
    for (Def def : ALL) {
      if (def.name().equals(name)) {
        return def;
      }
    }
    List<String> names = ALL.stream().map(Def::name).toList();
    throw new Refusal("No tool " + name + ". The tools are " + String.join(", ", names) + ".");
  }

  /**
   * Runs one call of {@code def}: its arguments checked, then its handler, as one change in the
   * journal, so {@code undo} can revert it; a call that fails leaves nothing changed.
   */
  static String call(Def def, SourceProject project, Map<String, Object> values) throws Exception {
    Args args = new Args(def.name(), values);
    args.check(def.args());
    Journal.begin(def.name());
    try {
      String text = def.handler().run(project, args);
      Journal.commit(project);
      return text;
    } catch (Exception | Error e) {
      Journal.abort();
      throw e;
    }
  }

  /** "line 4" or "lines 4, 7, 9". */
  static String lines(List<Integer> lines) {
    return (lines.size() == 1 ? "line " : "lines ")
        + lines.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }
}
