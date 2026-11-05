package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

    /** A tool whose schema and argument names are built from {@code args}. */
    Def(String name, String description, List<Arg> args, Kind kind, Handler handler) {
      this(
          name,
          description,
          Arg.schemaOf(args),
          args.stream().map(Arg::name).collect(Collectors.toSet()),
          kind,
          handler);
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
          - pattern (a Java regular expression), with file or without: the lines that match, \
          numbered, file by file over the project's Java files (at most 100): where a name is \
          used, which line to edit. Use it rather than grep.
          Paths are relative to the project root; lines are 1-based.\
          """,
          List.of(
              Arg.string(
                  "file",
                  "Java file path relative to the project root; optional with pattern, to search"
                      + " every Java file."),
              Arg.integer("start_line", "First line to show (1-based)."),
              Arg.integer("end_line", "Last line to show (inclusive)."),
              Arg.string(
                  "pattern", "Optional: show the lines that match this Java regular expression.")),
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
          method, a new name that is not a valid Java name, that the type already has, that it \
          or a supertype has for a method of as many parameters (an overload), that a local \
          would hide at a use of the field, or that a nested, enclosing or subclass declaration \
          would capture (a renamed use would mean it, or a use of it the renamed one).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line of the declaration or any use.").required(),
              Arg.string(
                      "name",
                      "The current name; Type.name for a member of Type, or local name for a "
                          + "local, when the line has both.")
                  .required(),
              Arg.integer(
                  "column",
                  "Optional 1-based column of the name, only to tell two meanings apart."),
              Arg.string("new_name", "The new name; a valid Java name.").required()),
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
          as=constant, field or parameter makes it a private static final constant or private \
          final field of the type, or a new parameter of the method that every call passes it \
          to, instead of a local; it must then be literals and constants only.
          Applies the edit, then type-checks the project: an edit that adds compile errors is \
          rolled back and refused.
          Refuses, saying why: an expression with an effect that would run in another order \
          (after an earlier effect of its statement) or at another time (out of a branch, a \
          loop or a lambda); one in a loop that reads a variable; one that may throw, out from \
          under its guard (right of && or ||) or out of a try that catches it; one that reads \
          what an earlier effect could change; assigned to; a statement not directly in a \
          block; or a type that does not resolve. The first call of a statement can be named: \
          f(p.next(), p.next()) gives a = p.next(); f(a, p.next()).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line on which the expression starts.").required(),
              Arg.string("expression", "Exact source text of the expression, e.g. price * qty.")
                  .required(),
              Arg.string("name", "Name for the new variable.").required(),
              Arg.integer(
                  "column",
                  "Optional 1-based column where the expression starts, only when its "
                      + "text occurs twice on the line."),
              Arg.oneOf(
                  "as",
                  "Optional: a private static final constant or private final field of "
                      + "the type, or a new parameter of the method (calls pass the "
                      + "expression), instead of a local. The expression must be literals and "
                      + "constants.",
                  "constant",
                  "field",
                  "parameter")),
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
          without an initializer; a variable assigned after its declaration; an initializer with \
          an effect used twice, or moved past another effect or into a branch, loop or lambda; \
          one that reads state a call or assignment before a use could change; a declared type \
          different from the initializer's.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line on which the variable is declared or used.")
                  .required(),
              Arg.string("name", "The variable's name.").required(),
              Arg.integer(
                  "column",
                  "Optional 1-based column of the name, only when the line has two "
                      + "variables of that name.")),
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
          a call nested in another call's arguments; a method with no calls.
          An argument that inlined in place would run at another time (an effect dropped, used \
          twice, after another effect, in a branch, loop or lambda, or out of order) is bound \
          to a local before the call's statement instead, with the arguments before it: \
          both(reserve(t), charge(t)) gives a = reserve(t); b = charge(t); a && b. Refused when \
          that move is unsafe too (it would cross an effect earlier in the statement).\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file path relative to the project root; the file that declares "
                      + "the method."),
              Arg.string("name", "Type.method, the static method to inline.").required()),
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
          For a change of or to a primitive or boxed type, a use whose result the type decides \
          is refused: an operand of arithmetic, a shift, ++ or a compound assignment, an \
          argument of a call, a cast, a branch of ?:, == on a boxed type (int to long loses an \
          overflow, int to double changes x / 2, char to int prints a number). Copies, returns \
          and comparisons are fine.
          Refuses, saying why: a name that is not a field of the class, a declaration of several \
          fields, a varargs parameter, such a use, or a change that adds compile errors, naming \
          them.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file path relative to the project root; the file that declares "
                      + "the field."),
              Arg.string(
                      "name",
                      "Type.field, the field whose type to change; or Type.method (with "
                          + "parameter types for an overload) with parameter or return_type.")
                  .required(),
              Arg.string("type", "The new type as written, such as long or List<String>.")
                  .required(),
              Arg.string(
                  "parameter",
                  "Optional: change this parameter's type in the method and every " + "override."),
              Arg.bool(
                  "return_type",
                  "Optional: change the method's return type, in it and every override."),
              Arg.IMPORTS),
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
          List.of(
              Arg.FILE.described(
                  "Java file path relative to the project root; the file that declares "
                      + "the type."),
              Arg.string("name", "The top-level class or interface to add the supertype to.")
                  .required(),
              Arg.oneOf(
                      "relation",
                      "extends for a superclass, or an interface's supertypes; implements for "
                          + "a class's interfaces.",
                      "extends",
                      "implements")
                  .required(),
              Arg.string("supertype", "The supertype as written, such as Base or List<String>.")
                  .required()),
          Kind.REFACTORING,
          Tools::addSupertype);

  static final Def EXTRACT_SUPERTYPE =
      new Def(
          "extract_supertype",
          """
          Extract a new interface or superclass from members of a class, in a file of its own in \
          the class's package. kind=interface: the new interface declares the signatures of the \
          named public instance methods, and the class implements it. kind=class: the named \
          methods and fields move into the new class (as pull_up moves them), which takes the \
          class's place in its extends and extends the old superclass.
          Type-checks the project after each step; any refusal changes nothing.
          Refuses, saying why: an interface or generic class, a name whose file exists, a member \
          not found, a method that is not public or is static (kind=interface), or what pull_up \
          refuses (kind=class).\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the class, relative to the project root."),
              Arg.string("name", "The top-level class to extract from.").required(),
              Arg.string("supertype", "The new type's simple name.").required(),
              Arg.oneOf("kind", "interface or class.", "interface", "class").required(),
              Arg.strings(
                      "members",
                      "The members, by name (with parameter types for an overload: "
                          + "price(int)).")
                  .required()),
          Kind.REFACTORING,
          (project, args) ->
              ExtractSupertype.run(
                  project,
                  args.required("file"),
                  args.required("name"),
                  args.required("supertype"),
                  args.required("kind"),
                  args.list("members")));

  static final Def CONVERT_LAMBDA =
      new Def(
          "convert_lambda",
          """
          Rewrite a lambda, a method reference or an anonymous class of a functional interface as \
          another of the three, where it means the same: s -> s.length() becomes String::length, \
          String::length becomes (s) -> s.length(), and an anonymous Runnable with its one run \
          method becomes () -> { ... }, or back. Identify it by file and the line it starts on \
          (column when several start there); to is lambda, anonymous or method_reference.
          Type-checks the project: a form that does not compile there (an ambiguous overload) is \
          refused.
          Refuses, saying why: a lambda whose body is not one call passing its parameters in \
          order (for a method reference); a receiver evaluated once by a reference and at each \
          call by a lambda that may change (a field, a call); this, or a bare call of the \
          interface's or Object's methods, which would mean another object; an anonymous class \
          with fields or other methods.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line it starts on.").required(),
              Arg.integer("column", "Optional 1-based column, when several start on the line."),
              Arg.oneOf("to", "What it becomes.", "lambda", "anonymous", "method_reference")
                  .required()),
          Kind.REFACTORING,
          (project, args) ->
              ConvertLambda.run(
                  project,
                  args.required("file"),
                  args.integer("line"),
                  args.integer("column"),
                  args.required("to")));

  static final Def CONVERT_TO_RECORD =
      new Def(
          "convert_to_record",
          """
          Turn a class whose instance fields are final into a record: the fields become its \
          components, its one constructor (which only assigns each field from its parameter, in \
          order) is the canonical one, an accessor x() becomes public, and its other members stay. \
          name is a top-level class of file.
          A record generates value-based equals, hashCode and toString for those the class does \
          not define: pass value_semantics=true to accept that change.
          Refuses, saying why: a superclass, a subclass, an abstract class, a field that is not \
          final or has an initializer, an instance initializer, other constructors, a missing \
          equals, hashCode or toString without value_semantics, or a change that adds compile \
          errors.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the class, relative to the project root."),
              Arg.string("name", "The top-level class.").required(),
              Arg.bool(
                  "value_semantics",
                  "Accept the record's generated equals, hashCode and toString where the "
                      + "class has none.")),
          Kind.REFACTORING,
          (project, args) ->
              ConvertToRecord.run(
                  project,
                  args.required("file"),
                  args.required("name"),
                  args.flag("value_semantics")));

  static final Def EXTRACT_STRUCT =
      new Def(
          "extract_struct",
          """
          Introduce a parameter object: consecutive parameters of a method become one parameter of \
          a new record, in a file beside the method's class: cost(int weight, int width, int \
          height) with parameters [width, height], struct Size and parameter size becomes \
          cost(int weight, Size size), each body reads size.width() for width, and every call \
          passes new Size(3, 4). Every override and implementation changes too.
          Extract class: with fields [street, city], name Person, struct Address and field \
          home, the fields move into a new class Address, Person holds one in home, and every \
          use becomes home.street; refused for a field used outside the class, or whose \
          initializer reads anything but constants.
          Refuses, saying why: parameters that are not next to each other, a body that assigns \
          one, a method lambdas implement, a varargs parameter, a name in use, or a change that \
          adds compile errors.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.member, or with (parameter types) for an overload.")
                  .required(),
              Arg.strings("parameters", "The parameters to group, next to each other."),
              Arg.string("struct", "The new record's name.").required(),
              Arg.string("parameter", "The new parameter's name."),
              Arg.strings(
                  "fields",
                  "Extract class instead: instance fields of the class name names, moved "
                      + "into the new class."),
              Arg.string("field", "With fields: the name of the field that holds the new class."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          ExtractStruct::run);

  static final Def FUNCTION_TO_METHOD =
      new Def(
          "function_to_method",
          """
          Make a static method whose first parameter is of its own class an instance method of \
          that class, the parameter its receiver: static int area(Shape s, int k) becomes int \
          area(int k), s in the body becomes this, and Shape.area(x, 3) becomes x.area(3) (area(2) \
          for a call passing this). The other way, instance to static in place, is move_member \
          with to set to the method's own class.
          A null first argument now fails at the call, not where the body used it: reported as a \
          change to exceptional behavior, refused under preserve_exceptions.
          Refuses, saying why: a method that is not static, a first parameter not of its class \
          or varargs, a body that assigns it, lambdas or method references of it, an instance \
          method of the new signature in a subclass or supertype (it would override or be \
          overridden).\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.member, or with (parameter types) for an overload.")
                  .required(),
              Arg.IMPORTS),
          Kind.REFACTORING,
          FunctionToMethod::run);

  static final Def REPLACE_EXPRESSION =
      new Def(
          "replace_expression",
          """
          Replace one expression with new text: an edit. Find it by file, the line it starts on \
          and its exact source text (column when the text occurs twice on the line), as \
          extract_variable does. The project must type-check no worse after; what new calls that \
          nothing declares is declared as stubs; imports are added and removed.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line the expression starts on.").required(),
              Arg.string("expression", "Its exact source text.").required(),
              Arg.integer(
                  "column", "Optional 1-based column, when the text occurs twice on the line."),
              Arg.string("new", "The expression to put in its place.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::replaceExpression);

  static final Def INSERT_STATEMENTS =
      new Def(
          "insert_statements",
          """
          Insert statements before or after the statement that starts on a line (directly in a \
          block): an edit. content is the statements, at any indentation; they are indented as \
          that statement is. The project must type-check no worse after; stubs and imports as \
          for every edit.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("content", "The statements.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::insertStatements);

  static final Def REPLACE_STATEMENTS =
      new Def(
          "replace_statements",
          """
          Replace whole statements of one block, from the one starting on start_line to the one \
          ending on end_line, with content (statements, re-indented); empty content deletes them: \
          an edit. The project must type-check no worse after; stubs and imports as for every \
          edit.
          Refuses an end_line that does not end a statement of that block, naming the lines that do.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "1-based line the first statement starts on.").required(),
              Arg.integer("end_line", "1-based line the last statement ends on.").required(),
              Arg.string("content", "The new statements; empty to delete.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::replaceStatements);

  static final Def ADD_CASE =
      new Def(
          "add_case",
          """
          Add a case to the switch (statement or expression) that starts on a line: before its \
          default, or at its end: an edit. content is the case with its statements \
          (case 2: return "two"; or case 2 -> "two";), indented as the other cases.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line the switch starts on.").required(),
              Arg.string("content", "The case.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::addCase);

  static final Def ADD_ELEMENT =
      new Def(
          "add_element",
          """
          Add an element at the end of the array initializer ({1, 2}) or the enum constants that \
          start on a line: an edit. content is the element (3, or RED). An element on a line of \
          its own goes on a new line, as the others.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line the initializer or enum starts on.").required(),
              Arg.string("content", "The element.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::addElement);

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
          A return, break, continue, throw or yield never moves, and neither a statement with a \
          side effect nor one that may throw moves past one that may leave the block early (an \
          if that returns), nor a call past a read of what it could change. Applies the edit, then type-checks the project: a swap that \
          adds compile errors is rolled back and refused.
          Refuses, saying why: no statement starts on the line, a statement with no neighbour in \
          that direction, a shared name, two side effects under the strict rule, a jump, or a side \
          effect past an early exit.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "The 1-based line the statement starts on.").required(),
              Arg.oneOf(
                      "direction",
                      "up swaps with the statement before it; down with the one after.",
                      "up",
                      "down")
                  .required(),
              Arg.bool(
                  "names_only",
                  "Relax the side-effect rule: only a shared name that one statement "
                      + "writes refuses the swap.")),
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
          List.of(Arg.FILE, Arg.integer("line", "The 1-based line the if starts on.").required()),
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
          List.of(Arg.FILE, Arg.integer("line", "The 1-based line the if starts on.").required()),
          Kind.REFACTORING,
          Tools::removeElse);

  static final Def MOVE_MEMBER =
      new Def(
          "move_member",
          """
          Move a method of a top-level class to another top-level class of the same file, or, \
          with to its own class, make it static in place. An \
          instance method becomes static in the target and takes the instance it used as its first \
          parameter, named receiver; every call in the project passes that instance. A private \
          method is widened to package access. The name is Type.method, or Type.method(params) for \
          an overload; to is the target class.
          Refuses, saying why: an override, a method another class overrides, a constructor, a \
          method reference to it, a call of itself, super, an anonymous or local class in the body, \
          a call from another class with no instance, or a change that adds compile errors, naming \
          them. A null instance that the method dereferences fails inside it, not at the call.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file path relative to the project root; the file that declares "
                      + "both classes."),
              Arg.string(
                      "name",
                      "Type.method, or Type.method(params) for an overload: the method to "
                          + "move.")
                  .required(),
              Arg.string("to", "The other top-level class of the file that the method moves to.")
                  .required()),
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
          List.of(
              Arg.FILE.described(
                  "Java file path relative to the project root; the file that declares "
                      + "the field."),
              Arg.string("name", "Type.field, the field to encapsulate.").required(),
              Arg.string(
                  "getter", "The getter's name; default get plus the field's name capitalized."),
              Arg.string(
                  "setter", "The setter's name; default set plus the field's name capitalized.")),
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
          List.of(
              Arg.FILE.described("Java file that declares the type, relative to the project root."),
              Arg.string(
                      "name",
                      "The nested type, as Outer.Inner; with package, the file's top-level "
                          + "type.")
                  .required(),
              Arg.string(
                  "package",
                  "Optional: move the file's only top-level type to this package instead "
                      + "(same source root).")),
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
          List.of(
              Arg.FILE.described(
                  "Java file that declares the class, relative to the project root."),
              Arg.string("name", "The class and the method, as Sub.method.").required(),
              Arg.string(
                  "to",
                  "Optional simple name of a direct supertype, when the superclass is not "
                      + "the one to pull into.")),
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
          List.of(
              Arg.FILE.described(
                  "Java file that declares the class, relative to the project root."),
              Arg.string("name", "The class and the method, as Sub.method.").required()),
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
          List.of(
              Arg.FILE.described("Path relative to the project root."),
              Arg.string("content", "The whole file.").required(),
              Arg.bool("overwrite", "Replace the file if it exists.")),
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
          List.of(
              Arg.FILE.described("The file or directory, relative to the project root."),
              Arg.string("to", "Its new path, relative to the project root."),
              Arg.bool("copy", "Copy it, keeping the original."),
              Arg.bool("delete", "Delete it instead (no to).")),
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
          old must be as in the file and occur once, or set all_occurrences. append, in place of \
          old, adds new at the end of the file (a friction-log entry, a CI job). In a .java file the \
          project must type-check no worse after, or nothing changes; what it calls that nothing \
          declares is declared as stubs, as by add_declaration. Imports the new text needs \
          are added and those it leaves unused removed; imports chooses an ambiguous name.\
          """,
          List.of(
              Arg.FILE.described("Path relative to the project root."),
              Arg.string("old", "Text as it is in the file."),
              Arg.string("new", "The text to put in its place.").required(),
              Arg.bool("all_occurrences", "Replace every occurrence, not only one."),
              Arg.bool("append", "Instead of old: add new at the end of the file."),
              Arg.IMPORTS),
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
          List.of(
              Arg.FILE,
              Arg.string("content", "Java member declarations.").required(),
              Arg.string("type", "Optional: the type to add to, as Outer or Outer.Inner."),
              Arg.string("after", "Optional: put them just after this member (a name)."),
              Arg.string("before", "Optional: put them just before this member."),
              Arg.IMPORTS),
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
          List.of(
              Arg.FILE,
              Arg.string(
                      "name",
                      "Type, Type.member or member; with (parameter types) for an overload.")
                  .required(),
              Arg.string("content", "The new declaration; empty to delete it.").required(),
              Arg.IMPORTS),
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
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload.")
                  .required(),
              Arg.string("parameter", "The new parameter's name.").required(),
              Arg.string("type", "Its Java type.").required(),
              Arg.string("value", "The Java expression each call passes.").required(),
              Arg.string("before", "Optional: the parameter to put it before; default last."),
              Arg.IMPORTS),
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
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload.")
                  .required(),
              Arg.string("parameter", "The parameter to remove.").required()),
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
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload.")
                  .required(),
              Arg.string("parameter", "The parameter to move.").required(),
              Arg.string("before", "Optional: the parameter to put it before; default last.")),
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
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "First line of the statements (1-based).").required(),
              Arg.integer("end_line", "Last line of the statements (inclusive).").required(),
              Arg.string("name", "The new method's name.").required(),
              Arg.string("doc", "Optional: its Javadoc text, without /** */.")),
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
          List.of(),
          Kind.CONTROL,
          (project, args) -> Outcome.of(Journal.undo(project)));

  static final Def BATCH =
      new Def(
          "batch",
          """
          Run several refactorings and edits in order as one change, planned from one read. Each \
          step is {"tool": ..., "args": {...}, "id": optional}: a tool (rename, or as the client \
          names it, mcp__jrefactor__rename) and the arguments it takes alone.
          Give line numbers as in the code you read before the batch: the batch moves them past \
          earlier steps. A line an earlier step rewrote cannot be given by number; name a line \
          an earlier step changed instead: "$prev.start", "$prev.end", "$2.end", "$id.start+1".
          If a step is refused, nothing changes and the result says which step and why. check \
          "end" type-checks once, after the last step, so edits may pass through code that does \
          not compile yet (a signature, then its callers). One undo reverts the whole batch.\
          """,
          List.of(
              Arg.json(
                      "steps",
                      """
                      {
                        "type": "array",
                        "description": "The steps, in order.",
                        "items": {
                          "type": "object",
                          "properties": {
                            "tool": {
                              "type": "string"
                            },
                            "args": {
                              "type": "object"
                            },
                            "id": {
                              "type": "string"
                            }
                          },
                          "required": [
                            "tool"
                          ]
                        }
                      }\
                      """)
                  .required(),
              Arg.oneOf(
                  "check",
                  "Type-check after each step (default), or once at the end.",
                  "each",
                  "end")),
          Kind.CONTROL,
          (project, args) -> Batch.run(project, args));

  static final Def FOLD =
      new Def(
          "fold",
          """
          Replace code that does what an existing static method does with calls of it: every \
          match of the method's body in the project, its parameters standing for any expressions \
          of their types, becomes a call. The method stays. extract_method makes a method from \
          one place; fold reuses one, written or extracted, everywhere else.
          What a body matches: one return's expression matches expressions (x * 1.2 becomes \
          withTax(x) for static double withTax(double a) { return a * 1.2; }); a void method's \
          statements match the same statements in any block, a local it declares standing for a \
          local the code declares and does not use after them. Every other name must mean what it \
          means in the body. So near-duplicates become calls: write the method with the \
          differences as parameters, then fold it. In a String.format of the body, a %s whose \
          argument is a parameter also matches constant text: "%s: %s", room, what matches \
          "%s: temp", room, and the call passes "temp".
          With line (and in), only the matches that start on that line are folded.
          A match the call would not reproduce is left, and the result says why: arguments run in \
          another order or another number of times, one moved out of a branch or past an effect, \
          a private method called from another class.
          Refuses, saying why: a method that is not static or has no body, a body that is not one \
          return or a void method's statements, a parameter the body does not use, varargs, or no \
          match a call can replace.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, relative to the project root."),
              Arg.string("name", "Type.method: the static method whose body to fold.").required(),
              Arg.integer(
                  "line",
                  "Optional: fold only the matches that start on this line (of in, or of file)."),
              Arg.string("in", "Optional, with line: the file whose line it is, if not file.")),
          Kind.REFACTORING,
          Tools::fold);

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
          FOLD,
          CHANGE_TYPE,
          ADD_SUPERTYPE,
          EXTRACT_SUPERTYPE,
          CONVERT_LAMBDA,
          CONVERT_TO_RECORD,
          EXTRACT_STRUCT,
          FUNCTION_TO_METHOD,
          REPLACE_EXPRESSION,
          INSERT_STATEMENTS,
          REPLACE_STATEMENTS,
          ADD_CASE,
          ADD_ELEMENT,
          MOVE_STATEMENT,
          INVERT_IF,
          REMOVE_ELSE,
          MOVE_MEMBER,
          ENCAPSULATE_FIELD,
          BATCH,
          UNDO);

  private static Outcome inspect(SourceProject project, Args args) throws Exception {
    String pattern = args.string("pattern");
    Integer start = args.integer("start_line");
    Integer end = args.integer("end_line");
    if (pattern != null) {
      if (start != null || end != null) {
        throw new Refusal(
            "pattern searches all of a file (or, with no file, the project); leave out"
                + " start_line and end_line, or the pattern.");
      }
      return Outcome.of(Inspect.pattern(project, args.string("file"), pattern));
    }
    String file = args.required("file");
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
        args.integer("column"),
        args.string("as"));
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

  private static Outcome fold(SourceProject project, Args args) throws Exception {
    return Fold.run(
        project,
        args.required("file"),
        args.required("name"),
        args.integer("line"),
        args.string("in"));
  }

  /**
   * The tool named {@code name}, or by the name a client gives it ({@code mcp__jrefactor__rename},
   * whatever the server is called there), or a refusal listing the tools there are.
   */
  static Def named(String name) {
    Matcher client = CLIENT_NAME.matcher(name);
    String bare = client.matches() ? client.group(1) : name;
    for (Def def : ALL) {
      if (def.name().equals(bare)) {
        return def;
      }
    }
    List<String> names = ALL.stream().map(Def::name).toList();
    throw new Refusal("No tool " + name + ". The tools are " + String.join(", ", names) + ".");
  }

  /** A tool's name as an MCP client gives it: mcp__, the server's name, __, the tool's. */
  private static final Pattern CLIENT_NAME = Pattern.compile("mcp__.+?__(\\w+)");

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
