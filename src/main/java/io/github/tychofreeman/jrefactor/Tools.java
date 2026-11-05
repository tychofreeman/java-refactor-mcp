package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** The server's tools: each one's name, description, schema and what a call does. */
final class Tools {

  private Tools() {}

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

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Every refactoring takes preserve_exceptions: its schema and its arguments gain it here. */
    Def {
      if (kind == Kind.REFACTORING) {
        ObjectNode root = (ObjectNode) JSON.readTree(schema);
        ObjectNode properties =
            root.get("properties") instanceof ObjectNode p ? p : root.putObject("properties");
        properties
            .putObject(Exceptional.FLAG)
            .put("type", "boolean")
            .put(
                "description",
                "Optional: true to keep exceptional behavior (where an exception is thrown, or"
                    + " whether one is): the tool keeps it or refuses. Without it, the changes it"
                    + " makes to it are listed in a part of the result of their own.");
        schema = root.toString();
        Set<String> all = new HashSet<>(args);
        all.add(Exceptional.FLAG);
        args = Set.copyOf(all);
      }
    }

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
          implementations); a top-level type takes its file (Plant.java becomes Herb.java); a \
          type parameter is renamed in its class or method, @param <T> included.
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

  static final Def EXTRACT_VARIABLE =
      new Def(
          "extract_variable",
          """
          Name an expression: a declaration is inserted just before its statement and the name \
          is used in its place. Extracting price * qty as subtotal in return price * qty + tax \
          gives int subtotal = price * qty; return subtotal + tax;.
          Identify the expression by file, the line it starts on and its exact source text. When \
          the text occurs twice on that line, pass column, the 1-based column where it starts.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused.
          Refuses, saying why: an expression in a loop condition or update, below && or ||, \
          inside a conditional or lambda, assigned to, one that calls, assigns or changes a \
          variable (its order would change), a statement not directly in a block, or a type \
          that does not resolve.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":       {"type": "string",  "description": "Java file path relative to the project root."},
              "line":       {"type": "integer", "description": "1-based line on which the expression starts."},
              "expression": {"type": "string",  "description": "Exact source text of the expression, e.g. price * qty."},
              "name":       {"type": "string",  "description": "Name for the new variable."},
              "column":     {"type": "integer", "description": "Optional 1-based column where the expression starts, only when its text occurs twice on the line."}
            },
            "required": ["file", "line", "expression", "name"]
          }\
          """,
          Set.of("file", "line", "expression", "name", "column"),
          Kind.REFACTORING,
          Tools::extractVariable);

  static final Def INLINE_VARIABLE =
      new Def(
          "inline_variable",
          """
          Replace every use of a local variable with its initializer, and delete the declaration: \
          the inverse of extract_variable. int subtotal = price * qty; return subtotal + tax; \
          becomes return (price * qty) + tax;.
          Identify the variable by file, a line on which it is declared or used, and its name. \
          When the name means two variables on the line, pass column.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused.
          Refuses, saying why: a field or a parameter; a declaration of several variables or \
          without an initializer; a variable assigned after its declaration; an initializer that \
          calls, assigns or changes a variable; a variable the initializer reads that is assigned \
          after the declaration; a declared type different from the initializer's.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":   {"type": "string",  "description": "Java file path relative to the project root."},
              "line":   {"type": "integer", "description": "1-based line on which the variable is declared or used."},
              "name":   {"type": "string",  "description": "The variable's name."},
              "column": {"type": "integer", "description": "Optional 1-based column of the name, only when the line has two variables of that name."}
            },
            "required": ["file", "line", "name"]
          }\
          """,
          Set.of("file", "line", "name", "column"),
          Kind.REFACTORING,
          Tools::inlineVariable);

  static final Def INLINE_FUNCTION =
      new Def(
          "inline_function",
          """
          Replace every call of a method in the project with its body, the arguments put \
          in place of the parameters, then delete the method. return a + tax(a) with \
          static double tax(double x) { return x * 0.2; } becomes return a + (a * 0.2);.
          Identify the method as Type.method, a top-level class of the file that declares it.
          A body of several statements: a void method's call becomes its statements; a method \
          ending in its one return must be called as all of an initializer, a return or an \
          assignment's value, and its other statements go before that statement.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused.
          An instance method's receiver takes the place of this, and of the bare members the \
          body uses (c.total() with return count * 2 becomes c.count * 2).
          Refuses, saying why: a varargs, generic or abstract method; an instance method that \
          another overrides, or called on a receiver that is not a local, parameter or this, or \
          whose body uses super; a body that \
          returns before its last statement; a body that assigns a parameter, or declares a name \
          the caller uses; a method that calls itself; a method used as Type::name; \
          a call nested in another call's arguments; a parameter used twice when its argument \
          calls or assigns; a method with no calls; an argument that calls, assigns or reads a \
          field when an effect of the body runs before its use, or its use is in a lambda or \
          (for one with an effect) a branch, or an argument with an effect and one that reads \
          state used in the other order than passed: put it in a variable first.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root; the file that declares the method."},
              "name": {"type": "string", "description": "Type.method, the static method to inline."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name"),
          Kind.REFACTORING,
          Tools::inlineFunction);

  static final Def CHANGE_TYPE =
      new Def(
          "change_type",
          """
          Change the declared type of a field, so that its uses must still compile: the field \
          Price.total of type int becomes long. Identify the field as Type.field, a top-level \
          class of the file.
          Applies the edit, then type-checks the project: a change that a use no longer compiles \
          with is rolled back and refused.
          A method's parameter (parameter) or return type (return_type=true) changes in it and \
          every override and implementation, and in lambdas that write the parameter's type; \
          calls are not edited, so one the new type breaks is refused with the error.
          Refuses, saying why: a name that is not a field of the class, a declaration of several \
          fields, a varargs parameter, or a change that adds compile errors, naming them.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root; the file that declares the field."},
              "name": {"type": "string", "description": "Type.field, the field whose type to change; or Type.method (with parameter types for an overload) with parameter or return_type."},
              "type": {"type": "string", "description": "The new type as written, such as long or List<String>."},
              "parameter": {"type": "string", "description": "Optional: change this parameter's type in the method and every override."},
              "return_type": {"type": "boolean", "description": "Optional: change the method's return type, in it and every override."},
              "imports": {"type": "array", "items": {"type": "string"}, "description": "Optional: fully qualified names to import where a simple name is ambiguous."}
            },
            "required": ["file", "name", "type"]
          }\
          """,
          Set.of("file", "name", "type", "parameter", "return_type", "imports"),
          Kind.REFACTORING,
          Tools::changeType);

  static final Def ADD_SUPERTYPE =
      new Def(
          "add_supertype",
          """
          Add a supertype to a top-level class or interface, in its header: relation extends or \
          implements, and the supertype as written, such as Base or Priced. Identify the type by \
          name, a top-level type of the file.
          Applies the edit, then type-checks the project: a supertype that does not resolve, or a \
          change that adds compile errors, is rolled back and refused.
          Refuses, saying why: a name that is not a top-level type of the file, extends on a class \
          that already extends one, implements on an interface, or a change that adds compile \
          errors, naming them.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root; the file that declares the type."},
              "name": {"type": "string", "description": "The top-level class or interface to add the supertype to."},
              "relation": {"type": "string", "enum": ["extends", "implements"], "description": "extends for a superclass, or an interface's supertypes; implements for a class's interfaces."},
              "supertype": {"type": "string", "description": "The supertype as written, such as Base or List<String>."}
            },
            "required": ["file", "name", "relation", "supertype"]
          }\
          """,
          Set.of("file", "name", "relation", "supertype"),
          Kind.REFACTORING,
          Tools::addSupertype);

  static final Def MOVE_STATEMENT =
      new Def(
          "move_statement",
          """
          Swap a statement with the one before it (direction up) or after it (direction down), in \
          the same block. Identify the statement by the line it starts on.
          The two statements must be independent. Strict (the default): they share no name that \
          one of them writes, and at most one of them has a side effect (a call, an assignment, a \
          new object). names_only=true relaxes that to the shared-name rule only, so two calls can \
          swap order: pass it only when the order of the calls does not matter.
          A return, break, continue, throw or yield never moves. Applies the edit, then type-checks \
          the project: a swap that adds compile errors is rolled back and refused.
          Refuses, saying why: no statement starts on the line, a statement with no neighbour in \
          that direction, a shared name, two side effects under the strict rule, or a jump.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root."},
              "line": {"type": "integer", "description": "The 1-based line the statement starts on."},
              "direction": {"type": "string", "enum": ["up", "down"], "description": "up swaps with the statement before it; down with the one after."},
              "names_only": {"type": "boolean", "description": "Relax the side-effect rule: only a shared name that one statement writes refuses the swap."}
            },
            "required": ["file", "line", "direction"]
          }\
          """,
          Set.of("file", "line", "direction", "names_only"),
          Kind.REFACTORING,
          Tools::moveStatement);

  static final Def INVERT_IF =
      new Def(
          "invert_if",
          """
          Invert an if that has an else: its branches swap and its condition is negated, so the \
          code runs the same. Identify the if by the line it starts on.
          The condition is negated by its opposite operator (== and !=, < and >=, <= and >), a \
          !x loses its !, and any other condition is wrapped in !(...). An ordering comparison on \
          a float or double is refused: it is not the opposite when a value is NaN.
          Refuses, saying why: no if on the line, an if with no else, an else if chain (invert the \
          inner if), a float comparison, or a change that adds compile errors, naming them.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root."},
              "line": {"type": "integer", "description": "The 1-based line the if starts on."}
            },
            "required": ["file", "line"]
          }\
          """,
          Set.of("file", "line"),
          Kind.REFACTORING,
          Tools::invertIf);

  static final Def REMOVE_ELSE =
      new Def(
          "remove_else",
          """
          Remove the else of an if whose then-branch cannot finish normally (it ends in a return, \
          throw, break, continue or yield). The else's statements then follow the if, at its \
          indentation, so the code runs the same. Identify the if by the line it starts on.
          Refuses, saying why: no if on the line, an if with no else, an if that is not directly \
          in a block, a then-branch that can finish normally (the else's statements would then run \
          after it too), or a change that adds compile errors, naming them.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root."},
              "line": {"type": "integer", "description": "The 1-based line the if starts on."}
            },
            "required": ["file", "line"]
          }\
          """,
          Set.of("file", "line"),
          Kind.REFACTORING,
          Tools::removeElse);

  static final Def MOVE_MEMBER =
      new Def(
          "move_member",
          """
          Move a method of a top-level class to another top-level class of the same file. An \
          instance method becomes static in the target and takes the instance it used as its first \
          parameter, named receiver; every call in the project passes that instance. A private \
          method is widened to package access. The name is Type.method, or Type.method(params) for \
          an overload; to is the target class.
          Refuses, saying why: an override, a method another class overrides, a constructor, a \
          method reference to it, a call of itself, super, an anonymous or local class in the body, \
          a call from another class with no instance, or a change that adds compile errors, naming \
          them. A null instance that the method dereferences fails inside it, not at the call.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root; the file that declares both classes."},
              "name": {"type": "string", "description": "Type.method, or Type.method(params) for an overload: the method to move."},
              "to": {"type": "string", "description": "The other top-level class of the file that the method moves to."}
            },
            "required": ["file", "name", "to"]
          }\
          """,
          Set.of("file", "name", "to"),
          Kind.REFACTORING,
          Tools::moveMember);

  static final Def ENCAPSULATE_FIELD =
      new Def(
          "encapsulate_field",
          """
          Encapsulate a field: it becomes private, with a getter and a setter (getX and setX, or the \
          names given), and every use in the project goes through them. A read becomes the getter \
          call; a plain assignment statement x = value becomes setX(value). A final field gets a \
          getter only. Identify the field as Type.field, a top-level class of the file.
          Refuses, saying why: a name that is not a field, a declaration of several fields, a final \
          field that is written, a compound write (+=) or ++ or --, a write inside an expression, a \
          statement that also reads the field, or a change that adds compile errors, naming them.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file path relative to the project root; the file that declares the field."},
              "name": {"type": "string", "description": "Type.field, the field to encapsulate."},
              "getter": {"type": "string", "description": "The getter's name; default get plus the field's name capitalized."},
              "setter": {"type": "string", "description": "The setter's name; default set plus the field's name capitalized."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name", "getter", "setter"),
          Kind.REFACTORING,
          Tools::encapsulateField);

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
          With package, the file's only top-level type moves to that package (same source root): \
          its file moves, imports and qualified names follow, files of its old package import it, \
          and package-private declarations used across the new boundary become public (named).
          Refuses, saying why: an inner (non-static) class, a top-level type without package, a \
          file of that name that exists, a file with several top-level types or in the default \
          package when moving to a package.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file that declares the type, relative to the project root."},
              "name": {"type": "string", "description": "The nested type, as Outer.Inner; with package, the file's top-level type."},
              "package": {"type": "string", "description": "Optional: move the file's only top-level type to this package instead (same source root)."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name", "package"),
          Kind.REFACTORING,
          Tools::moveType);

  static final Def PULL_UP =
      new Def(
          "pull_up",
          """
          Move a method or field from its class to its direct superclass, or to a direct supertype named by \
          to: the member leaves Sub and is added to the end of Base. Every call still compiles, \
          since Sub inherits the method. name is Sub.method.
          Type-checks the project: a move that adds compile errors (the method uses a field only Sub \
          has, say) is rolled back and refused.
          Refuses, saying why: no superclass or to that is not a direct supertype, a supertype that \
          is not in the project's sources, a member the supertype already declares, a method that \
          would override a concrete one the supertype inherits (its other subclasses would run it), \
          a bare name \
          that matches several overloads (they are listed; name one with its parameter types, as \
          Sub.hello(int)), a class that is not top-level.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file that declares the class, relative to the project root."},
              "name": {"type": "string", "description": "The class and the method, as Sub.method."},
              "to": {"type": "string", "description": "Optional simple name of a direct supertype, when the superclass is not the one to pull into."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name", "to"),
          Kind.REFACTORING,
          Tools::pullUp);

  static final Def PUSH_DOWN =
      new Def(
          "push_down",
          """
          Move a method or field from its class to each direct subclass in the project: the member leaves \
          Sub and is added to the end of each subclass. name is Sub.method.
          Type-checks the project: a caller that uses the method through Sub (Sub s; s.hello()) \
          no longer compiles, so the move is rolled back and refused.
          Refuses, saying why: no direct subclass in the project, a subclass that already declares \
          the member, a method that overrides a supertype's method, a bare name that matches several \
          overloads (they are listed; name one with its parameter types), a class that is not top-level.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file": {"type": "string", "description": "Java file that declares the class, relative to the project root."},
              "name": {"type": "string", "description": "The class and the method, as Sub.method."}
            },
            "required": ["file", "name"]
          }\
          """,
          Set.of("file", "name"),
          Kind.REFACTORING,
          Tools::pushDown);

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

  static final Def MOVE_FILE =
      new Def(
          "move_file",
          """
          Move or copy a file or directory that is not Java under a source root (a fixture, a doc, \
          a golden case), or delete it with delete=true: an edit. Missing directories are \
          created, and directories a move or delete empties are removed. undo puts every file back.
          Refuses, saying why: a path that does not exist, a to that does, a Java file under a \
          source root (move_type moves types), a file that is not UTF-8 text.\
          """,
          """
          {
            "type": "object",
            "properties": {
              "file":   {"type": "string",  "description": "The file or directory, relative to the project root."},
              "to":     {"type": "string",  "description": "Its new path, relative to the project root."},
              "copy":   {"type": "boolean", "description": "Copy it, keeping the original."},
              "delete": {"type": "boolean", "description": "Delete it instead (no to)."}
            },
            "required": ["file"]
          }\
          """,
          Set.of("file", "to", "copy", "delete"),
          Kind.EDIT,
          (project, args) ->
              MoveFile.run(
                  project,
                  args.required("file"),
                  args.string("to"),
                  args.flag("copy"),
                  args.flag("delete")));

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
          Javadoc; a file's only top-level type takes the file with it (undo restores it).
          name is Type, Type.member, or member (of the file's only top-level type); for one of \
          several overloads, add its parameter types: Cart.add(int, String).
          The old Javadoc stays unless content has its own. Callers a new signature breaks are \
          refused. A deletion is refused while anything uses it (the uses are named), and for a \
          method that overrides a concrete one, whose callers would then run the inherited one.
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
          declaration it overrides or that overrides it changes too, and every lambda that \
          implements it ((a, b) -> a becomes (a, b, retries) -> a). The body does not use it \
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
          overrides or that overrides it changes too, and every lambda that implements it. Use \
          it last when taking a feature out: \
          first change the bodies so they no longer read it.
          name finds the method as replace_declaration does. A dropped argument must have no \
          side effects (a name, a literal, this, a field).
          Refuses, saying why: a body or lambda that reads the parameter, an argument with side \
          effects, a method that overrides a library method, one used as a method reference, \
          varargs.\
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
          family (what it overrides and what overrides it), every lambda that implements it, and \
          every call's arguments: moving \
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
          become parameters; the one they set or declare that later code uses is returned, \
          and several are returned in a record named after the method (sum and count as \
          TotalsResult, taken out after the call: int sum = totalsResult.sum();). \
          The method is static when the enclosing one is, and declares the checked exceptions \
          the statements throw. doc becomes its Javadoc.
          Refuses, saying why: a range that cuts a statement (with the statement lines there), \
          a break, continue or yield that leaves the statements, a return \
          in the middle of a void method, this(...) or super(...), an assignment to a final \
          field, a name the type already has.\
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
          PULL_UP,
          PUSH_DOWN,
          CREATE_FILE,
          MOVE_FILE,
          REPLACE_TEXT,
          ADD_DECLARATION,
          REPLACE_DECLARATION,
          ADD_PARAMETER,
          REMOVE_PARAMETER,
          MOVE_PARAMETER,
          EXTRACT_METHOD,
          EXTRACT_VARIABLE,
          INLINE_VARIABLE,
          INLINE_FUNCTION,
          CHANGE_TYPE,
          ADD_SUPERTYPE,
          MOVE_STATEMENT,
          INVERT_IF,
          REMOVE_ELSE,
          MOVE_MEMBER,
          ENCAPSULATE_FIELD,
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
    if (args.has("package")) {
      return MoveToPackage.run(
          project, args.required("file"), args.required("name"), args.required("package"));
    }
    return MoveType.run(project, args.required("file"), args.required("name"));
  }

  private static Outcome extractVariable(SourceProject project, Args args) throws Exception {
    Integer line = args.integer("line");
    if (line == null) {
      throw new Refusal("extract_variable needs line, the 1-based line the expression starts on.");
    }
    return ExtractVariable.run(
        project,
        args.required("file"),
        line,
        args.required("expression"),
        args.required("name"),
        args.integer("column"));
  }

  private static Outcome inlineVariable(SourceProject project, Args args) throws Exception {
    Integer line = args.integer("line");
    if (line == null) {
      throw new Refusal(
          "inline_variable needs line, a line on which the variable is declared or used.");
    }
    return InlineVariable.run(
        project, args.required("file"), line, args.required("name"), args.integer("column"));
  }

  private static Outcome inlineFunction(SourceProject project, Args args) throws Exception {
    return InlineFunction.run(project, args.required("file"), args.required("name"));
  }

  private static Outcome changeType(SourceProject project, Args args) throws Exception {
    if (args.has("parameter") || args.flag("return_type")) {
      return Signatures.changeType(project, args);
    }
    return ChangeType.run(
        project, args.required("file"), args.required("name"), args.required("type"));
  }

  private static Outcome addSupertype(SourceProject project, Args args) throws Exception {
    return AddSupertype.run(
        project,
        args.required("file"),
        args.required("name"),
        args.required("relation"),
        args.required("supertype"));
  }

  private static Outcome moveStatement(SourceProject project, Args args) throws Exception {
    return MoveStatement.run(
        project,
        args.required("file"),
        args.integer("line"),
        args.required("direction"),
        args.flag("names_only"));
  }

  private static Outcome invertIf(SourceProject project, Args args) throws Exception {
    return InvertIf.run(project, args.required("file"), args.integer("line"));
  }

  private static Outcome removeElse(SourceProject project, Args args) throws Exception {
    return RemoveElse.run(project, args.required("file"), args.integer("line"));
  }

  private static Outcome moveMember(SourceProject project, Args args) throws Exception {
    return MoveMember.run(
        project, args.required("file"), args.required("name"), args.required("to"));
  }

  private static Outcome encapsulateField(SourceProject project, Args args) throws Exception {
    return EncapsulateField.run(
        project,
        args.required("file"),
        args.required("name"),
        args.has("getter") ? args.required("getter") : null,
        args.has("setter") ? args.required("setter") : null);
  }

  private static Outcome pullUp(SourceProject project, Args args) throws Exception {
    return PullUp.run(project, args.required("file"), args.required("name"), args.string("to"));
  }

  private static Outcome pushDown(SourceProject project, Args args) throws Exception {
    return PushDown.run(project, args.required("file"), args.required("name"));
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
    Session session = project.session();
    boolean outer = session.editing;
    boolean outerPreserve = session.preserveExceptions;
    List<String> outerExceptional = session.exceptional;
    session.editing = def.kind() == Kind.EDIT;
    session.preserveExceptions = def.kind() == Kind.REFACTORING && args.flag(Exceptional.FLAG);
    session.exceptional = new ArrayList<>();
    try {
      Outcome outcome = def.handler().run(project, args).withExceptional(session.exceptional);
      Journal.commit(project);
      // A step's changes to exceptional behavior are its batch's too.
      outerExceptional.addAll(session.exceptional);
      return outcome;
    } catch (Exception | Error e) {
      abort(project, e);
      throw e;
    } finally {
      // A batch remembers that one of its steps was an edit, for its check at the end.
      session.editing = outer || def.kind() == Kind.EDIT;
      session.preserveExceptions = outerPreserve;
      session.exceptional = outerExceptional;
    }
  }

  /** Undoes the open call after {@code failure}; an undo that fails is added to it, not thrown. */
  static void abort(SourceProject project, Throwable failure) {
    try {
      Journal.abort(project);
    } catch (java.io.IOException undo) {
      failure.addSuppressed(undo);
    }
  }
}
