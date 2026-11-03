package io.github.tychofreeman.jrefactor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** The server's tools: each one's name, description, schema and what a call does. */
final class Tools {

  private Tools() {}

  /**
   * What a successful call returned: its text, and the stubs it declared, which the text names but
   * which are carried apart so that a caller can check them.
   */
  record Outcome(String text, Stubs.Declared declared) {

    static Outcome of(String text) {
      return new Outcome(text, Stubs.Declared.NONE);
    }

    /** The text the model reads: the call's text with the stubs it declared named after it. */
    String render() {
      return text + declared.text();
    }
  }

  /** What a call does: its outcome, or a {@link Refusal}. */
  interface Handler {
    Outcome run(SourceProject project, Args args) throws Exception;
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
      Kind kind,
      Handler handler) {

    /** Whether the tool changes nothing. */
    boolean readOnly() {
      return kind == Kind.READ;
    }
  }

  /** What a tool does, for its annotations and for {@link SessionReport}'s counts. */
  enum Kind {
    /** Reads code and changes nothing. */
    READ,
    /** Changes code without changing what it does, everywhere it is used. */
    REFACTORING,
    /** Writes the text it is given. */
    EDIT,
    /** Runs or reverts other tools' changes. */
    CONTROL
  }

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
          Kind.READ,
          Tools::inspect);

  static final Def RENAME =
      new Def(
          "rename",
          """
          Rename a local variable, parameter, field, method or type, and every reference to it in \
          the project, Javadoc included: renaming the field price of Item to cost changes its \
          declaration, every item.price and this.price, and {@link Item#price}. A method takes \
          every declaration it overrides or that overrides it (an interface method and its \
          implementations); a top-level type takes its file (Plant.java becomes Herb.java).
          Identify it by file, line (its declaration or any use) and its current name. When the \
          name means two things on the line (this.count = count), name the one you mean: \
          "Counter.count" for the member of Counter, "local count" for the local or parameter, \
          or pass column.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused. Returns the lines changed in each file.
          Refuses, saying why: a declaration of a library, a method that overrides a library \
          method, a new name that is not a valid Java name, that the type already has, or that \
          a local would hide at a use of the field.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":     {"type": "string",  "description": "Java file path relative to the project root."},
              "line":     {"type": "integer", "description": "1-based line of the declaration or any use."},
              "name":     {"type": "string",  "description": "The current name; Type.name for a member of Type, or local name for a local, when the line has both."},
              "column":   {"type": "integer", "description": "Optional 1-based column of the name, only to tell two meanings apart."},
              "new_name": {"type": "string",  "description": "The new name; a valid Java name."}
            },
            "required": ["file", "line", "name", "new_name"]
          }\
          """,
          Set.of("file", "line", "name", "column", "new_name"),
          Kind.REFACTORING,
          Tools::rename);

  static final Def MOVE_TYPE =
      new Def(
          "move_type",
          """
          Move a static nested type (class, interface, enum or record) to its own file, as a \
          top-level type of the same package: Shop.Line in Shop.java becomes Line in Line.java. \
          Every reference in the project follows (Shop.Line becomes Line, import a.Shop.Line \
          becomes import a.Line), the moved type's uses of Shop's static members are qualified \
          (label(x) becomes Shop.label(x)), and private members either side uses across the new \
          boundary become package-private; the result names them.
          name is Outer.Inner, as written in file. Type-checks the project: a move that adds \
          compile errors is rolled back and refused.
          Refuses, saying why: an inner (non-static) class, a top-level type, a file of that \
          name that exists.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file that declares the type, relative to the project root."},
              "name": {"type": "string", "description": "The nested type, as Outer.Inner."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name"),
          Kind.REFACTORING,
          Tools::moveType);

  static final Def CREATE_FILE =
      new Def(
          "create_file",
          """
          Create a new file, Java or not (a test, a fixture, a doc), with any missing directories: \
          an edit.
          A .java file must parse and declare the package its directory implies under a source \
          root, and the project must type-check no worse after. Methods, constructors and \
          classes of the project it uses that nothing declares are declared as stubs (a test \
          written first compiles); other new errors are refused.
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
          Kind.EDIT,
          (project, args) -> Edits.createFile(project, args));

  static final Def REPLACE_TEXT =
      new Def(
          "replace_text",
          """
          Replace exact text in a file: an edit, for what the other tools do not cover: string \
          literals, comments, a few lines of code, and files that are not Java (pom.xml, \
          fixtures, docs).
          old must be as in the file and occur once, or set all_occurrences. In a .java file the \
          project must type-check no worse after, or nothing changes; what it calls that nothing \
          declares is declared as stubs, as by add_declaration. Imports the new text needs \
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
          Kind.EDIT,
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
          Methods, constructors and classes of the project the members use that nothing declares \
          are declared as such stubs, typed from the use; the result names them ("declared \
          stubs"). Write a test first and call what does not exist yet.
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
          Kind.EDIT,
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
          Kind.EDIT,
          (project, args) -> Declarations.replace(project, args));

  static final Def ADD_PARAMETER =
      new Def(
          "add_parameter",
          """
          Add a parameter to a method or constructor, and pass the same value for it at every \
          call in the project: with parameter retries, type int and value 3, void send(String to) \
          becomes void send(String to, int retries), and send(addr) becomes send(addr, 3). Every \
          declaration it overrides or that overrides it changes too. The body does not use it \
          yet, so behavior is unchanged; add it, then change the body.
          name finds the method as replace_declaration does (Type.member, member, m(int, String) \
          for an overload; a constructor by its type's name). value is a Java expression every \
          call can see, with no side effects. before puts the parameter ahead of that one \
          (default: last).
          Refuses, saying why: a method that overrides a library method, one used as a method \
          reference, varargs, a name that would hide a field a body reads, a value that does \
          not type-check at some call.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":      {"type": "string", "description": "Java file that declares the method, relative to the project root."},
              "name":      {"type": "string", "description": "Type.member, member, or with (parameter types) for an overload."},
              "parameter": {"type": "string", "description": "The new parameter's name."},
              "type":      {"type": "string", "description": "Its Java type."},
              "value":     {"type": "string", "description": "The Java expression each call passes."},
              "before":    {"type": "string", "description": "Optional: the parameter to put it before; default last."},
              "imports":   {"type": "array", "items": {"type": "string"}, "description": "Optional: fully qualified names to import where a simple name is ambiguous."}
            },
            "required": ["file", "name", "parameter", "type", "value"]
          }\
          """,
          Set.of("file", "name", "parameter", "type", "value", "before", "imports"),
          Kind.REFACTORING,
          (project, args) -> Signatures.addParameter(project, args));

  static final Def REMOVE_PARAMETER =
      new Def(
          "remove_parameter",
          """
          Remove a parameter no body reads, with its @param tag, and its argument at every call \
          in the project: removing retries, void send(String to, int retries) becomes \
          void send(String to), and send(addr, 3) becomes send(addr). Every declaration it \
          overrides or that overrides it changes too. Use it last when taking a feature out: \
          first change the bodies so they no longer read it.
          name finds the method as replace_declaration does. A dropped argument must have no \
          side effects (a name, a literal, this, a field).
          Refuses, saying why: a body that reads the parameter, an argument with side effects, \
          a method that overrides a library method, one used as a method reference, varargs.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":      {"type": "string", "description": "Java file that declares the method, relative to the project root."},
              "name":      {"type": "string", "description": "Type.member, member, or with (parameter types) for an overload."},
              "parameter": {"type": "string", "description": "The parameter to remove."}
            },
            "required": ["file", "name", "parameter"]
          }\
          """,
          Set.of("file", "name", "parameter"),
          Kind.REFACTORING,
          (project, args) -> Signatures.removeParameter(project, args));

  static final Def MOVE_PARAMETER =
      new Def(
          "move_parameter",
          """
          Move a parameter to another place in the list, in every declaration of the method's \
          family (what it overrides and what overrides it) and every call's arguments: moving \
          to before from, void copy(Path from, Path to) becomes void copy(Path to, Path from), \
          and copy(a, b) becomes copy(b, a).
          name finds the method as replace_declaration does; before names the parameter to put \
          it ahead of (default: last).
          Refuses, saying why: two arguments with side effects whose order would change, a \
          method that overrides a library method, one used as a method reference, varargs.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":      {"type": "string", "description": "Java file that declares the method, relative to the project root."},
              "name":      {"type": "string", "description": "Type.member, member, or with (parameter types) for an overload."},
              "parameter": {"type": "string", "description": "The parameter to move."},
              "before":    {"type": "string", "description": "Optional: the parameter to put it before; default last."}
            },
            "required": ["file", "name", "parameter"]
          }\
          """,
          Set.of("file", "name", "parameter", "before"),
          Kind.REFACTORING,
          (project, args) -> Signatures.moveParameter(project, args));

  static final Def EXTRACT_METHOD =
      new Def(
          "extract_method",
          """
          Move whole statements into a new private method, declared after the one they are in, \
          and call it in their place: lines 9-12 of print, which sum values into total, become \
          int total = sum(values); and a method int sum(List<Integer> values). Behavior is \
          unchanged.
          start_line and end_line (inclusive, as inspect numbers them) cover whole statements \
          of one block; comments and blank lines may be in the range. The locals they use \
          become parameters; the one they set or declare that later code uses is returned. \
          The method is static when the enclosing one is, and declares the checked exceptions \
          the statements throw. doc becomes its Javadoc.
          Refuses, saying why: a range that cuts a statement (with the statement lines there), \
          two or more results, a break, continue or yield that leaves the statements, a return \
          in the middle of a void method, a name the type already has.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":       {"type": "string",  "description": "Java file path relative to the project root."},
              "start_line": {"type": "integer", "description": "First line of the statements (1-based)."},
              "end_line":   {"type": "integer", "description": "Last line of the statements (inclusive)."},
              "name":       {"type": "string",  "description": "The new method's name."},
              "doc":        {"type": "string",  "description": "Optional: its Javadoc text, without /** */."}
            },
            "required": ["file", "start_line", "end_line", "name"]
          }\
          """,
          Set.of("file", "start_line", "end_line", "name", "doc"),
          Kind.REFACTORING,
          (project, args) -> Extract.method(project, args));

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
          Kind.CONTROL,
          (project, args) -> Outcome.of(Journal.undo(project)));

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
          Kind.CONTROL,
          (project, args) -> Batch.run(project, args));

  static final List<Def> ALL =
      List.of(
          INSPECT,
          RENAME,
          MOVE_TYPE,
          CREATE_FILE,
          REPLACE_TEXT,
          ADD_DECLARATION,
          REPLACE_DECLARATION,
          ADD_PARAMETER,
          REMOVE_PARAMETER,
          MOVE_PARAMETER,
          EXTRACT_METHOD,
          BATCH,
          UNDO);

  private static Outcome inspect(SourceProject project, Args args) throws Exception {
    String file = args.required("file");
    Integer start = args.integer("start_line");
    Integer end = args.integer("end_line");
    if (start == null && end == null) {
      return Outcome.of(Inspect.file(project, file));
    }
    return Outcome.of(
        Inspect.lines(
            project, file, start == null ? 1 : start, end == null ? Integer.MAX_VALUE : end));
  }

  private static Outcome rename(SourceProject project, Args args) throws Exception {
    String file = args.required("file");
    String name = args.required("name");
    String newName = args.required("new_name");
    Integer line = args.integer("line");
    if (line == null) {
      throw new Refusal("rename needs line: the 1-based line of a use or declaration.");
    }
    return Rename.run(project, file, line, name, args.integer("column"), newName);
  }

  private static Outcome moveType(SourceProject project, Args args) throws Exception {
    return MoveType.run(project, args.required("file"), args.required("name"));
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
  static Outcome call(Def def, SourceProject project, Map<String, Object> values) throws Exception {
    Args args = new Args(def.name(), values);
    args.check(def.args());
    Journal.begin(project, def.name());
    try {
      Outcome outcome = def.handler().run(project, args);
      Journal.commit(project);
      return outcome;
    } catch (Exception | Error e) {
      Journal.abort(project);
      throw e;
    }
  }
}
