package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
   * One tool. {@code arguments} are the arguments it takes; the schema leaves out {@code
   * "additionalProperties": false}, since with it the SDK refuses an unknown argument before the
   * tool can name the ones to use.
   */
  record Def(
      String name,
      String description,
      String schema,
      List<Arg> arguments,
      Kind kind,
      Handler handler) {

    private static final ObjectMapper JSON = Json.MAPPER;

    /** Every refactoring takes preserve_exceptions: its schema and its arguments gain it here. */
    Def {
      if (kind == Kind.REFACTORING
          && arguments.stream().noneMatch(a -> a.name().equals(Exceptional.FLAG))) {
        Arg flag =
            Arg.bool(
                Exceptional.FLAG,
                "Optional: true to refuse a change to where or whether an exception is thrown.");
        ObjectNode root = (ObjectNode) JSON.readTree(schema);
        ObjectNode properties =
            root.get("properties") instanceof ObjectNode p ? p : root.putObject("properties");
        properties.set(flag.name(), flag.schema());
        schema = root.toString();
        List<Arg> all = new ArrayList<>(arguments);
        all.add(flag);
        arguments = List.copyOf(all);
      }
    }

    /** A tool whose schema is built from {@code args}. */
    Def(String name, String description, List<Arg> args, Kind kind, Handler handler) {
      this(name, description, Arg.schemaOf(args), args, kind, handler);
    }

    /** The names of the arguments it takes. */
    Set<String> args() {
      return arguments.stream().map(Arg::name).collect(Collectors.toSet());
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
          Read Java code with line numbers, for exact lines.
          - no arguments: the source roots, packages and files.
          - file: its bodies of two lines or more elided, each method's lines, complexity, \
          nesting and side effects given; with function (Type.method, or the method) that \
          method; with start_line/end_line those lines.
          - targets: several of those at once, each {file, function} or {file, start_line, \
          end_line}.
          - pattern (a Java regular expression), with file or without: the matching lines (at \
          most 100). Use it rather than grep.
          - names: declarations by name, anywhere: Type (bodies elided) or Type.member.
          - uses: every use of a type, field or method, by binding, under what it does with it \
          (compared, written, called, read, ...).\
          """,
          List.of(
              Arg.string("file", "Java file path; optional with pattern."),
              Arg.string("function", "With file: Type.method, or the method's name."),
              Arg.integer("start_line", "First line to show (1-based)."),
              Arg.integer("end_line", "Last line to show (inclusive)."),
              Arg.json(
                  "targets",
                  """
                  {"type": "array", "description": "Views of files.", "items": {"type": "object"}}
                  """),
              Arg.string(
                  "pattern", "Optional: show the lines that match this Java regular expression."),
              Arg.strings("names", "Optional, alone: Type or Type.member, anywhere."),
              Arg.strings(
                  "uses",
                  "Optional, alone: Type or Type.member, each use listed by what it does.")),
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
          Identify it by file and name, with line (its declaration or any use) unless name is \
          Type.member (Type.method(int) for one overload). When the name means two \
          things on the line (this.count = count), name the one you mean: "Counter.count" for \
          the member of Counter, "local count" for the local or parameter, or pass column.
          Returns the lines changed in each file.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line of the declaration or any use."),
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
          as=constant, field or parameter makes it a private static final constant or private \
          final field of the type, or a new parameter of the method, instead of a local; as a \
          parameter, each call passes it with its own arguments in their place (qty / 10 becomes \
          bonus, and total(n + 1, 3) passes (n + 1) / 10).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "Line the expression starts on.").required(),
              Arg.string("expression", "Its exact source text, e.g. price * qty.").required(),
              Arg.string("name", "Name for the new variable.").required(),
              Arg.integer(
                  "column", "Optional: its column, when its text occurs twice on the line."),
              Arg.oneOf(
                  "as",
                  "Optional: what to make it instead of a local.",
                  "constant",
                  "field",
                  "parameter"),
              Arg.bool(
                  "all_occurrences",
                  "Optional, with as=parameter: every occurrence in the method becomes "
                      + "the parameter.")),
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
          When the name means two variables on the line, pass column.\
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
          An instance method's receiver takes the place of this, and of the bare members the \
          body uses (c.total() with return count * 2 becomes c.count * 2).\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares " + "the method."),
              Arg.string("name", "Type.method, the static method to inline.").required()),
          Kind.REFACTORING,
          Tools::inlineFunction);

  static final Def CHANGE_TYPE =
      new Def(
          "change_type",
          """
          Change a field's type: name Price.total and type long make int total long total.
          With parameter or return_type=true, a method's type changes in it, its overrides and \
          implementations, and lambdas that write it: void put(int qty) becomes void put(long \
          qty).
          With type_parameter (T), type is T's bound in the class or method and every override: \
          <T> T max(List<T> xs) becomes <T extends Comparable<T>> T max(List<T> xs) with type \
          Comparable<T>; Object removes it. Calls and uses are not edited.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares it."),
              Arg.string("name", "Type.field, Type.method or a class."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("type", "The new type as written, or the bound.").required(),
              Arg.string("parameter", "Optional: the parameter whose type changes."),
              Arg.bool("return_type", "Optional: true to change the return type."),
              Arg.string("type_parameter", "Optional: a type parameter of name."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          Tools::changeType);

  static final Def CHANGE_SIGNATURE =
      new Def(
          "change_signature",
          """
          Change a method's return type or declared exceptions, in every override and \
          implementation too: return_type=void and add_throws=IOException turn boolean \
          apply(Rename r) into void apply(Rename r) throws IOException. Each caller declares a \
          new checked exception, up to stop_at or a method that cannot (Runnable.run()), where \
          it is caught and rethrown unchecked. Other calls are not edited.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares it."),
              Arg.string("name", "Type.method, or with parameter types."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("return_type", "Optional: the new return type."),
              Arg.string("add_throws", "Optional: exception types to declare, comma-separated."),
              Arg.string("remove_throws", "Optional: exception types to stop declaring."),
              Arg.string("callers", "Optional: propagate (default), catch or refuse."),
              Arg.string("stop_at", "Optional: methods where propagating ends."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          (project, args) -> ChangeSignature.run(project, ByLine.named(project, args)));

  static final Def ADD_SUPERTYPE =
      new Def(
          "add_supertype",
          """
          Add a supertype to a top-level class or interface, in its header: relation extends or \
          implements, and the supertype as written, such as Base or Priced. Identify the type by \
          name, a top-level type of the file.\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares " + "the type."),
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
          class's place in its extends and extends the old superclass. From a generic class, \
          the new type declares the type parameters its members use, bounds included.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
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
          (column when several start there); to is lambda, anonymous or method_reference.\
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
          not define: pass value_semantics=true to accept that change.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
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

  static final Def EXTRACT_TYPE =
      new Def(
          "extract_type",
          """
          Group members into a new type in its own file, uses following; name says which:
          - A method's parameters: name Shipping.cost, members [width, height], new_type Size, \
          as size: cost(int w, int width, int height) becomes cost(int w, Size size), reading \
          size.width(); calls pass new Size(3, 4).
          - A class's fields (name Person, as home): Person holds a new Address home; uses \
          become home.street.
          - A record's components (as address): new Order(id, s, c) becomes new Order(id, new \
          Address(s, c)), o.street() o.address().street().
          - static final int or String constants (members [OPEN, CLOSED], new_type State, no \
          as): an enum; their holders become State.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares it."),
              Arg.string("name", "A method (Type.member), class or record.").required(),
              Arg.strings("members", "Names to group.").required(),
              Arg.string("new_type", "The new type's name.").required(),
              Arg.string("as", "The holder's name."),
              Arg.string("accessor", "Constants: the old value's accessor."),
              Arg.bool("value_semantics", "Record: accept the changed toString."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          ExtractType::run);

  static final Def SPLIT =
      new Def(
          "split",
          """
          Split a method into two phases: the statements from line to the end of its body become \
          the method new_name, called in their place, and the locals the statements before line \
          compute for them are carried in a new record, type, in its own file. With int litres = \
          rows * 2; int extra = days > 3 ? 1 : 0; int total = litres * days + extra; return total \
          * rate; and line the third statement's, new_name charge and type Usage give return \
          charge(new Usage(litres, extra), days, rate); and int charge(Usage usage, int days, int \
          rate), which reads usage.litres() and usage.extra(); record Usage(int litres, int \
          extra).\
          """,
          List.of(
              Arg.string("kind", "phase.").required(),
              Arg.FILE,
              Arg.integer("line", "1-based line of the second phase's first statement.").required(),
              Arg.string("new_name", "The second phase's new method.").required(),
              Arg.string("type", "The new record of what the first phase computes.").required()),
          Kind.REFACTORING,
          Split::run);

  static final Def FUNCTION_TO_METHOD =
      new Def(
          "function_to_method",
          """
          Make a static method whose first parameter is of its own class an instance method of \
          that class, the parameter its receiver: static int area(Shape s, int k) becomes int \
          area(int k), s in the body becomes this, and Shape.area(x, 3) becomes x.area(3) (area(2) \
          for a call passing this). The other way, instance to static in place, is move \
          with to set to the method's own class.
          A null first argument now fails at the call, not where the body used it: reported as a \
          change to exceptional behavior.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.member, or with (parameter types) for an overload.")
                  .required(),
              Arg.IMPORTS),
          Kind.REFACTORING,
          FunctionToMethod::run);

  static final Def REPLACE_EXPRESSION =
      new Def(
          "replace_expression",
          """
          Replace one expression with new text. Find it by file, the line it starts on \
          and its exact source text (column when the text occurs twice on the line), as \
          extract_variable does. With build in place of new, the call or construction is written \
          from names: {"call": "discounts.apply", "args": ["price", "customer"]} writes \
          discounts.apply(price, customer), {"new": "ArrayList"} writes new ArrayList<>().
          A $name in expression stands for any expression, which new places: $s.road() to \
          Routes.of($s, "road"). everywhere=true replaces each expression of the project that \
          matches the one at line: names meaning the same, holes of the same types.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line the expression starts on.").required(),
              Arg.string("expression", "Its exact source text.").required(),
              Arg.bool("everywhere", "Every match in the project, not only at line."),
              Arg.integer(
                  "column", "Optional 1-based column, when the text occurs twice on the line."),
              Arg.string("new", "The expression to put in its place."),
              Arg.json(
                  "build",
                  """
                  {
                    "type": "object",
                    "description": "In place of new: the call or construction to write, by name: {call, args} or {new, args}, as build of insert_statements takes them."
                  }
                  """),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::replaceExpression);

  static final Def INSERT_STATEMENTS =
      new Def(
          "insert_statements",
          """
          Insert statements after (or before) the statement on line.
          With build, entries are written from names, types read from the code: {"call": \
          "prices.of", "args": ["order"], "into": "price"} writes Money price = \
          prices.of(order); (into a declared variable assigns, into return returns); {"new": \
          "ArrayList", "into": "open", "type": "List<Order>"} writes List<Order> open = new \
          ArrayList<>(); {"loop": "orders", "body": [...]} writes for (Order order : orders) { \
          ... } (form index counts); {"guard": "qty <= 0", "exit": "throw", "value": \
          "IllegalArgumentException"} writes if (qty <= 0) { throw new \
          IllegalArgumentException(); } (or return, continue, break); {"guard_null": "o", \
          "exit": "require"} writes Objects.requireNonNull(o);. A string is a statement as \
          typed.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "Line a statement starts on.").required(),
              Arg.oneOf("where", "Default after.", "before", "after"),
              Arg.string("content", "The statements, at any indentation."),
              Arg.BUILD,
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::insertStatements);

  static final Def REPLACE_STATEMENTS =
      new Def(
          "replace_statements",
          """
          Replace whole statements of one block, start_line to end_line, with content \
          (statements, re-indented); empty content deletes them.
          With surround in place of content (try, if (ready), for (Item i : items), synchronized \
          (lock)), the statements go inside that block; a try catches and rethrows the checked \
          exceptions they throw. surround {"loop": "orders"} writes the loop's head from names, \
          as build's loop does: for (Order order : orders) { ... }.
          With build in place of content, the new statements are written from names, as \
          insert_statements does.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "Line the first statement starts on.").required(),
              Arg.integer("end_line", "Line the last statement ends on.").required(),
              Arg.string("content", "The new statements."),
              Arg.BUILD,
              Arg.json(
                  "surround",
                  """
                  {
                    "description": "In place of content: a block's head, or a loop object of build without body.",
                    "anyOf": [
                      {"type": "string"},
                      {"type": "object"}
                    ]
                  }
                  """),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::replaceStatements);

  static final Def ADD_CASE =
      new Def(
          "add_case",
          """
          Add a case to the switch (statement or expression) that starts on a line: before its \
          default, or at its end. content is the case with its statements \
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
          start on a line. content is the element (3, or RED). An element on a line of \
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
          The two statements must be independent: by default they share no name that one of \
          them writes, and at most one of them has a side effect (a call, an assignment, a new \
          object). names_only=true keeps only the shared-name rule, so two calls can swap: pass \
          it only when the order of the calls does not matter. past_try=true swaps a statement \
          with a try next to it though their effects are ordered: below the try it then runs \
          only when the try completes normally, and the result says so.\
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
                      + "writes refuses the swap."),
              Arg.bool(
                  "past_try",
                  "Swap a statement with a try next to it whatever their side effects, and say"
                      + " what changes.")),
          Kind.REFACTORING,
          Tools::moveStatement);

  static final Def INVERT_IF =
      new Def(
          "invert_if",
          """
          Invert an if that has an else: its branches swap and its condition is negated, so the \
          code runs the same. Identify the if by the line it starts on.
          The condition is negated by its opposite operator (== and !=, < and >=, <= and >), a \
          !x loses its !, and any other condition is wrapped in !(...).\
          """,
          List.of(Arg.FILE, Arg.integer("line", "The 1-based line the if starts on.").required()),
          Kind.REFACTORING,
          Tools::invertIf);

  static final Def LOOP_TO_STREAM =
      new Def(
          "loop_to_stream",
          """
          Rewrite an enhanced for over a collection as a stream that runs the same code for each \
          element in the same order. An if with no else holding the rest of the body becomes \
          filter, a first local computed from the element map, and the rest forEach: for (Order o \
          : orders) { if (o.open()) ship(o); } becomes orders.stream().filter(o -> \
          o.open()).forEach(o -> ship(o)). A body that only adds to a list declared empty just \
          before the loop collects into it instead, addAll as flatMap: List<String> names = \
          items.stream().map(i -> i.name()).toList(), or collect(Collectors.toList()) when the \
          list is changed afterwards. Identify the loop by the line it starts on.\
          """,
          List.of(Arg.FILE, Arg.integer("line", "The 1-based line the loop starts on.").required()),
          Kind.REFACTORING,
          Tools::loopToStream);

  static final Def CONVERT_LOOP =
      new Def(
          "convert_loop",
          """
          Rewrite a loop as another statement form that runs the same code in the same order. \
          Identify the loop by the line it starts on; to is the form to write:
          - for_each: from an index for over a list or an array, for (int i = 0; i < \
          names.size(); i++) { print(names.get(i)); } becomes for (String name : names) { \
          print(name); }; or from a for over an iterator whose body starts with T e = it.next().
          - iterator, or for: from an enhanced for, a for over its iterator, or over the index of \
          a list or an array, the element read first in the body.
          - while: from a for, its init before the while and its update last in the body (and \
          before each continue); from a do-while, while (true) { body if (!c) break; }.
          - for: from a while, the local declared just before it and the body's last assignment \
          taking the init's and update's places.
          - do_while: from a while, if (c) { do { body } while (c); }.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "The 1-based line the loop starts on.").required(),
              Arg.string("to", "The form to write: for_each, iterator, for, while or do_while.")
                  .required()),
          Kind.REFACTORING,
          Tools::convertLoop);

  static final Def SUBCLASS_TO_STRATEGY =
      new Def(
          "subclass_to_strategy",
          """
          Turn a subclass that only overrides one method into a function its superclass holds: \
          with class FileRenamer extends Renamer { boolean move(Rename r) {...} }, Renamer gains \
          an interface Move and a field of it, its move calls the field, the override's body \
          becomes the static Renamer.moveFile and Renamer's own Renamer.defaultMove; each \
          constructor of Renamer takes the function, one with the old parameters passing \
          defaultMove; new FileRenamer(out) becomes new Renamer(out, Renamer::moveFile), \
          FileRenamer as a type becomes Renamer, and its file is deleted. type names a \
          functional interface to use in place of a new one.\
          """,
          List.of(
              Arg.FILE,
              Arg.string("name", "The subclass, e.g. FileRenamer.").required(),
              Arg.string("method", "The one method it overrides, e.g. move.").required(),
              Arg.string(
                  "type",
                  "Optional: a functional interface for the field, e.g. Predicate<Rename>;"
                      + " without it the tool declares one in the superclass.")),
          Kind.REFACTORING,
          Tools::subclassToStrategy);

  static final Def REMOVE_ELSE =
      new Def(
          "remove_else",
          """
          Remove the else of an if whose then-branch cannot finish normally (it ends in a return, \
          throw, break, continue or yield). The else's statements then follow the if, at its \
          indentation, so the code runs the same. Identify the if by the line it starts on.\
          """,
          List.of(Arg.FILE, Arg.integer("line", "The 1-based line the if starts on.").required()),
          Kind.REFACTORING,
          Tools::removeElse);

  static final Def MOVE_MEMBER =
      new Def(
          "move_member",
          """
          Move a method of a top-level class to another top-level class, in the same file or \
          another (to=Tax, or com.example.Tax when several files declare one; public when it \
          crosses packages), or, with to its own class, make it static in place. A static field \
          (Shop.MAX) moves too, every use then naming the new class; its initializer must be \
          constants. An \
          instance method becomes static in the target and takes the instance it used as its first \
          parameter, named receiver; every call in the project passes that instance. A private \
          method is widened to package access. The name is Type.method, or Type.method(params) for \
          an overload; to is the target class.\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares " + "both classes."),
              Arg.string(
                      "name",
                      "Type.method, or Type.method(params) for an overload: the method to "
                          + "move.")
                  .required(),
              Arg.string(
                      "to",
                      "The top-level class the method moves to: of this file, or another (with its"
                          + " package when several share the name).")
                  .required(),
              Arg.IMPORTS),
          Kind.REFACTORING,
          Tools::moveMember);

  static final Def ENCAPSULATE_FIELD =
      new Def(
          "encapsulate_field",
          """
          Encapsulate a field: it becomes private, with a getter and a setter (getX and setX, or the \
          names given), and every use in the project goes through them. A read becomes the getter \
          call; an assignment statement x = value becomes setX(value), and x += value; or x++; becomes \
          setX(getX() + value). A final field gets a getter only. Identify the field as Type.field, a top-level class of the file.\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares " + "the field."),
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
          name is Outer.Inner, as written in file.
          With package, the file's only top-level type moves to that package (same source root): \
          its file moves, imports and qualified names follow, files of its old package import it, \
          and package-private declarations used across the new boundary become public (named).\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the type."),
              Arg.string(
                      "name",
                      "The nested type, as Outer.Inner; with package, the file's top-level "
                          + "type.")
                  .required(),
              Arg.string(
                  "package",
                  "Optional: move the file's only top-level type to this package instead "
                      + "(same source root)."),
              Arg.string(
                  "to",
                  "Optional: a top-level class of the package the nested type moves into, as"
                      + " its member type.")),
          Kind.REFACTORING,
          Tools::moveType);

  static final Def PULL_UP =
      new Def(
          "pull_up",
          """
          Move a method or field up from a class to its direct superclass, or to a direct \
          supertype named by to: with name Circle.describe, describe() leaves Circle and is added \
          to its superclass Shape, where every Shape then has it. Calls need no change, since \
          Circle inherits it.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
              Arg.string("name", "The class and its method or field, as Class.member.").required(),
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
          Move a method or field down from a class to each of its direct subclasses in the \
          project: with name Shape.describe, describe() leaves Shape and is added to Circle and \
          to Square.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
              Arg.string("name", "The class and its method or field, as Class.member.").required()),
          Kind.REFACTORING,
          Tools::pushDown);

  static final Def CREATE_FILE =
      new Def(
          "create_file",
          """
          Create a new file, Java or not (a test, a fixture, a doc), with any missing directories.
          A .java file must parse and declare the package its directory implies under a source \
          root.
          The file must not exist, unless overwrite is set to replace all of it.
          With declaration in place of content, a .java file starts as its type: the package \
          line and the declaration (final class Cart implements Priced), with a stub for each \
          method its supertypes leave abstract; add members after with add_declaration.
          With test_for in place of content (Cart.total, Cart.add(int) for one overload), a test \
          file starts from its target: JUnit's imports, and a red test method that builds the \
          receiver, calls the method and ends in fail; type the assertion in place of fail.\
          """,
          List.of(
              Arg.FILE.described("Path of the file."),
              Arg.string("content", "The whole file (or declaration in its place)."),
              Arg.string(
                  "declaration",
                  "For a .java file, in place of content: its type's header, such as final"
                      + " class Cart implements Priced or record Line(int qty)."),
              Arg.string(
                  "test_for",
                  "For a test file, in place of content: what it tests, as Type, Type.method or"
                      + " Type.method(int)."),
              Arg.bool("overwrite", "Replace the file if it exists.")),
          Kind.EDIT,
          (project, args) -> Edits.createFile(project, args));

  static final Def MOVE_FILE =
      new Def(
          "move_file",
          """
          Move or copy a file or directory that is not Java under a source root (a fixture, a doc, \
          a golden case), or delete it with delete=true. Missing directories are \
          created, and directories a move or delete empties are removed. undo puts every file back.\
          """,
          List.of(
              Arg.FILE.described("The file or directory."),
              Arg.string("to", "Its new path."),
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

  /**
   * One tool for every move (gorefactor's {@code docs/parity.md}): it routes to move_type,
   * move_member or move_file by its arguments, which stay names batch and the CLI accept.
   */
  static final Def MOVE =
      new Def(
          "move",
          """
          Move a type, a member or a file; every reference follows. The arguments choose:
          - name Outer.Inner: the nested type to its own file (Line.java); with to, into that \
          class of its package (Shop.Line becomes Order.Line).
          - name Type with package: the file's top-level type to that package (Cart to \
          com.shop.model); package-private declarations used across become public.
          - name Type.member with to: a method or static field to that class (Tax or \
          com.example.Tax); an instance method becomes static, its instance the first \
          parameter, receiver. To its own class, made static in place.
          - no name, to a path: the file or directory (docs/a.md to docs/b.md); copy keeps it, \
          delete=true removes it.\
          """,
          List.of(
              Arg.FILE.described("Declares what moves; without name, the file or directory."),
              Arg.string("name", "Outer.Inner, Type or Type.member; omit to move the file."),
              Arg.string("to", "The class it moves into; without name, the new path."),
              Arg.string("package", "The package a type moves to."),
              Arg.bool("copy", "Copy the file, keeping the original."),
              Arg.bool("delete", "Delete the file."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          Tools::move);

  private static Outcome move(SourceProject project, Args args) throws Exception {
    String name = args.string("name");
    String to = args.string("to");
    String pkg = args.string("package");
    args.required("file");
    Def target;
    if (name == null) {
      if (pkg != null) {
        throw new Refusal("package moves a type: give name, the file's top-level type.");
      }
      target = MOVE_FILE;
    } else {
      if (args.flag("copy") || args.flag("delete")) {
        throw new Refusal(
            "copy and delete are for a file: leave out name to move the file itself.");
      }
      if (to != null && pkg != null) {
        throw new Refusal(
            "give to (the class a member moves to) or package (where the file's type moves), not"
                + " both.");
      }
      target =
          to == null || memberType(project, args.required("file"), name) ? MOVE_TYPE : MOVE_MEMBER;
    }
    if (target != MOVE_MEMBER && !args.list("imports").isEmpty()) {
      throw new Refusal(
          "imports is for moving a member (name Type.member with to), whose import fix takes it.");
    }
    return target.handler().run(project, args.routed(target.name(), target.arguments()));
  }

  /**
   * One tool for the inverses of extract (gorefactor's {@code docs/parity.md}): it routes to
   * inline_variable or inline_function by its arguments, which stay names batch and the CLI accept.
   */
  static final Def INLINE =
      new Def(
          "inline",
          """
          Inline what name names where it is used, and delete it when nothing else uses it; what \
          it is at line says which:
          - a method called on line: that call becomes the body, arguments for parameters (the \
          receiver for this): return a + tax(a) with static double tax(double x) { return x * \
          0.2; } becomes return a + (a * 0.2);. all_calls does every call (without line, name \
          Type.method).
          - a local variable declared or used on line: its initializer replaces every use.
          - with field, name a class (Person) and field its field (home) holding a class only it \
          uses (Address): Address's members take the field's place, home.street becomes street, \
          and Address.java is deleted.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file: of the call or the variable; without line, the method's."),
              Arg.string("name", "The method (Type.method) or the variable.").required(),
              Arg.integer("line", "Line of the call, or of the variable's declaration or a use."),
              Arg.integer("column", "Optional: the name's column, when the line has two."),
              Arg.bool("all_calls", "For a method: every call, then delete it."),
              Arg.bool("keep_function", "For a method: keep it."),
              Arg.string("field", "With name a class: the field whose class to fold in.")),
          Kind.REFACTORING,
          Tools::inline);

  static final Def ADD_RESULT =
      new Def(
          "add_result",
          """
          Make a method return one more value, which its callers ignore, so behavior is \
          unchanged until the method computes it: with name Cart.total, type int and result \
          count, int total(...) becomes TotalResult total(...), a record TotalResult(int value, \
          int count) declared after it, each return e; becomes return new TotalResult(e, 0); \
          and each call that uses the result reads total(...).value(). A void method returns \
          the type instead, each return giving its zero (0, false, null).\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.method, or with (parameter types) for an overload.")
                  .required(),
              Arg.string("type", "The new value's Java type.").required(),
              Arg.string("result", "The new value's name, a component of the record.").required()),
          Kind.REFACTORING,
          AddResult::run);

  static final Def TYPE_SWITCH_TO_METHOD =
      new Def(
          "type_switch_to_method",
          """
          Turn a switch over a value's type into a method on each type: with name area, return \
          switch (s) { case Circle c -> Math.PI * c.r() * c.r(); case Square q -> q.side() * \
          q.side(); } becomes return s.area();, Shape declares double area();, and Circle and \
          Square each override it with their case's expression, the pattern variable as the \
          receiver (c.r() becomes r()).
          A switch statement of arrow cases, or an if chain of s instanceof T v, makes a void \
          method of each case's statements; a default (or final else) becomes Shape's own \
          implementation. A case's block becomes the method's body, each yield a return.
          Over an enum, cases that each give a constant become a field: with name label, case \
          RED -> "r"; case GREEN, BLUE -> "cool"; gives RED("r"), GREEN("cool"), BLUE("cool"), \
          read by label(), and the switch becomes c.label(). Otherwise, or with as=method, each \
          constant a case names overrides label() in its own body with its case's body, this as \
          the switch's value (RED { @Override public String label() {...} }).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "Line the switch, or the first if, starts on.").required(),
              Arg.string("name", "The new method's name.").required(),
              Arg.string("as", "Optional, over an enum: field or method.")),
          Kind.REFACTORING,
          TypeSwitch::run);

  private static Outcome inline(SourceProject project, Args args) throws Exception {
    String name = args.required("name");
    if (args.string("field") != null) {
      if (args.integer("line") != null) {
        throw new Refusal("field folds a class into its holder; leave out line.");
      }
      return InlineClass.run(project, args.required("file"), name, args.string("field"));
    }
    args.required("file");
    boolean all = args.flag("all_calls");
    boolean keep = args.flag("keep_function");
    Integer line = args.integer("line");
    if (line != null) {
      Outcome call =
          InlineFunction.atCall(
              project, args.required("file"), line, name, args.integer("column"), keep, all);
      if (call != null) {
        return call;
      }
      return INLINE_VARIABLE
          .handler()
          .run(project, args.routed(INLINE_VARIABLE.name(), INLINE_VARIABLE.arguments()));
    } else if (name.contains(".")) {
      if (args.integer("column") != null) {
        throw new Refusal(
            "column is with line, of a call or a variable; a method is named Type.method.");
      }
      return InlineFunction.run(project, args.required("file"), name, null, keep);
    } else {
      throw new Refusal(
          "give line for the local variable "
              + name
              + " (a line it is declared or used on), or name a static method as Type."
              + name
              + " to inline every call of it.");
    }
  }

  static final Def REPLACE_TEXT =
      new Def(
          "replace_text",
          """
          Replace exact text in a file, for what the other tools do not cover: string \
          literals, comments, a few lines of code, and files that are not Java (pom.xml, \
          fixtures, docs).
          old must be as in the file and occur once, or set all_occurrences; spacing within lines \
          may differ (each run of spaces stands for any other), and then new moves to the file's \
          indentation (the result says so). append, in place of \
          old, adds new at the end of the file (a friction-log entry, a CI job). function \
          (Type.method) looks for old only in that method, its Javadoc included; files in place \
          of file makes the same replacement in each.\
          """,
          List.of(
              Arg.string("file", "Path of the file."),
              Arg.strings("files", "Instead of file: several files, the same replacement in each."),
              Arg.string("old", "Text as it is in the file."),
              Arg.string("new", "The text to put in its place.").required(),
              Arg.bool("all_occurrences", "Replace every occurrence, not only one."),
              Arg.string(
                  "function", "Optional: look for old only in this method of the Java file."),
              Arg.bool("append", "Instead of old: add new at the end of the file."),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) -> Edits.replaceTexts(project, args));

  static final Def ADD_DECLARATION =
      new Def(
          "add_declaration",
          """
          Add members (methods, fields, constructors, nested types) to a type, the file's only \
          top-level one unless type names it, each placed by its kind; content is the members at \
          any indentation. A member with a stub's signature replaces the stub, and a constructor \
          replaces one of its parameter types that only assigns fields.
          In place of content: test_for (Cart.total) writes a red test of that method; implement \
          writes stubs of abstract methods, or overrides that call super; constructor (field \
          names, in order) writes a constructor that assigns them.\
          """,
          List.of(
              Arg.FILE,
              Arg.string("content", "The members."),
              Arg.string(
                  "implement",
                  "In place of content: * for every abstract method the supertypes leave, or a"
                      + " supertype's method to override (toString, Base.describe(int))."),
              Arg.strings(
                  "constructor",
                  "In place of content: the fields a new constructor takes and assigns, in order."),
              Arg.string(
                  "test_for",
                  "In place of content, in a test class: the method to test, as Type.method or"
                      + " Type.method(int)."),
              Arg.string("type", "Optional: the type to add to, as Outer or Outer.Inner."),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) -> Declarations.add(project, args));

  static final Def REPLACE_DECLARATION =
      new Def(
          "replace_declaration",
          """
          Replace a whole declaration, found by name, with new source, for a signature \
          and body that change together, a field, or a type. Empty content deletes it, with its \
          Javadoc; a file's only top-level type takes the file with it (undo restores it).
          name is Type, Type.member, or member (of the file's only top-level type); for one of \
          several overloads, add its parameter types: Cart.add(int, String). With line, name is \
          what it names on that line of file (its declaration or a use), wherever it is declared.
          The old Javadoc stays unless content has its own.\
          """,
          List.of(
              Arg.FILE,
              Arg.string(
                      "name",
                      "Type, Type.member or member; with (parameter types) for an overload.")
                  .required(),
              Arg.string("content", "The new declaration; empty to delete it.").required(),
              Arg.integer("line", "Optional: a line of file with its declaration or a use."),
              Arg.integer("column", "Optional 1-based column of name, when the line has two."),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) ->
              Declarations.replace(
                  project,
                  args.integer("line") == null ? args : ByLine.declaration(project, args)));

  static final Def ADD_PARAMETER =
      new Def(
          "add_parameter",
          """
          Add a parameter to a method or constructor, and pass the same value at every call: \
          with parameter retries, type int and value 3, void send(String to) becomes void \
          send(String to, int retries), and send(addr) becomes send(addr, 3); overrides and \
          implementing lambdas too ((a, b) -> a becomes (a, b, retries) -> a). The body does not \
          use it yet.
          name may also be:
          - a record (Line): a new component, which every new Line(...) passes.
          - an enum (Color): a final field with an accessor; values {"RED": "\\"#f00\\""} gives \
          RED("#f00"), the rest pass value.
          - a class or method, with type_parameter (T extends Comparable<T>): class Box becomes \
          class Box<T> and Box b = new Box(x) becomes Box<String> b = new Box<>(x), value \
          String; static String first(List<String> xs) becomes static <T> T first(List<T> xs).\
          """,
          List.of(
              Arg.FILE.described("Java file that declares it."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The new parameter's name."),
              Arg.string("type", "Its type."),
              Arg.string("type_parameter", "In place of parameter and type: T or T extends Bound."),
              Arg.strings(
                  "members", "With type_parameter: members whose declared types become it."),
              Arg.string(
                  "value",
                  "The expression each call passes; with type_parameter, the type argument."),
              Arg.json(
                  "values",
                  """
                  {"type": "object", "description": "For an enum: constant to the expression \
                  it passes.", \
                  "additionalProperties": {"type": "string"}}\
                  """),
              Arg.string("before", "Optional: the parameter to put it before; default last."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          (project, args) ->
              args.has("type_parameter")
                  ? TypeParameters.add(project, args)
                  : Signatures.addParameter(project, ByLine.named(project, args)));

  static final Def REMOVE_PARAMETER =
      new Def(
          "remove_parameter",
          """
          Remove a parameter no body reads, with its @param tag, and its argument at every call: \
          removing retries, void send(String to, int retries) becomes void send(String to), and \
          send(addr, 3) becomes send(addr). Overrides, overridden declarations and implementing \
          lambdas change too.
          With value, what every call passes for it, the body may read it: a field or literal \
          (value out: report(PrintStream out, String r), called report(out, r), becomes \
          report(String r), reading the field out), or an expression of the other parameters, \
          fields and calls, each call's own arguments in their place (value width * height: \
          fits(int width, int height, int area), called fits(w, h + 1, w * (h + 1)), becomes \
          fits(int width, int height), called fits(w, h + 1), its body starting int area = width \
          * height;).\
          """,
          List.of(
              Arg.FILE.described("Java file that declares it."),
              Arg.string("name", "Type.member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The parameter to remove.").required(),
              Arg.string("value", "Optional: what every call passes for it.")),
          Kind.REFACTORING,
          (project, args) -> Signatures.removeParameter(project, ByLine.named(project, args)));

  static final Def MOVE_PARAMETER =
      new Def(
          "move_parameter",
          """
          Move a parameter to another place in the list, in every declaration of the method's \
          family (what it overrides and what overrides it), every lambda that implements it, and \
          every call's arguments: moving \
          to before from, void copy(Path from, Path to) becomes void copy(Path to, Path from), \
          and copy(a, b) becomes copy(b, a).
          name finds the method as replace_declaration does, or line as add_parameter's does; \
          before names the parameter to put it ahead of (default: last).\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The parameter to move.").required(),
              Arg.string("before", "Optional: the parameter to put it before; default last.")),
          Kind.REFACTORING,
          (project, args) -> Signatures.moveParameter(project, ByLine.named(project, args)));

  static final Def EXTRACT_FUNCTION =
      new Def(
          "extract_function",
          """
          Move whole statements into a new method, private by default, and call it in their \
          place: lines 9-12 of print, which sum values into total, become int total = \
          sum(values); and a method int sum(List<Integer> values).
          start_line and end_line (inclusive) cover whole statements of one block; comments and \
          blank lines may be in the range. The locals they use become parameters; the one they \
          set or declare that later code uses is returned, and several are returned in a record \
          named after the method (sum and count as TotalsResult, taken out after the call: int \
          sum = totalsResult.sum();). The method is static when the enclosing one is, and \
          declares the checked exceptions the statements throw. Not private, it may override a \
          supertype's method of its name and parameter types, as a template method's step, and \
          is marked @Override.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "First line of the statements.").required(),
              Arg.integer("end_line", "Last line of the statements.").required(),
              Arg.string("name", "The new method's name.").required(),
              Arg.string("doc", "Optional: its Javadoc text, without /** */."),
              Arg.string(
                  "visibility", "Optional: private (the default), package, protected or public.")),
          Kind.REFACTORING,
          (project, args) -> Extract.method(project, args));

  static final Def UNDO =
      new Def(
          "undo",
          """
          Undo the last change made through these tools in this session (one edit, refactoring \
          or whole batch), restoring each file it wrote. Call it again to step further back.\
          """,
          List.of(),
          Kind.CONTROL,
          (project, args) -> Outcome.of(Journal.undo(project)));

  static final Def BATCH =
      new Def(
          "batch",
          """
          Run several refactorings and edits in order as one change. Each step is {"tool": ..., \
          "args": {...}, "id": optional}: a tool (rename, or mcp__jrefactor__rename) and the \
          arguments it takes alone.
          Give line numbers as in the code you read before the batch: the batch moves them past \
          earlier steps. A line an earlier step rewrote cannot be given by number; name a line \
          an earlier step changed instead: "$prev.start", "$prev.end", "$2.end", "$id.start+1".
          The steps apply together or not at all. check "end" type-checks once, after the last \
          step, so edits may pass through code that does not compile yet (a signature, then its \
          callers). One undo reverts the whole batch.
          After a refusal, retry: true resends that batch with only the steps that change: \
          {"step": 3, "args": {...}} replaces step 3 (its tool too, if the step names one), \
          {"after": 2, "tool": ..., "args": {...}} adds a step after step 2, {"remove": 2} drops \
          step 2: remove and after together move a step.\
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
                            },
                            "step": {
                              "type": "integer"
                            },
                            "after": {
                              "type": "integer"
                            },
                            "remove": {
                              "type": "integer"
                            }
                          }
                        }
                      }\
                      """)
                  .required(),
              Arg.oneOf(
                  "check",
                  "Type-check after each step (default), or once at the end.",
                  "each",
                  "end"),
              Arg.bool("retry", "Resend the last refused batch with only these steps changed.")),
          Kind.CONTROL,
          (project, args) -> Batch.run(project, args));

  static final Def FOLD =
      new Def(
          "fold",
          """
          Replace code that does what an existing method does with calls of it: every match of \
          the method's body in the project, its parameters standing for any expressions of their \
          types, becomes a call. The method stays. An instance method's body matches code in \
          instance members of its class and subclasses, where this is the same object, and \
          becomes total(...) there. To merge two methods of one body, fold one, inline the other.
          What a body matches: one return's expression matches expressions (x * 1.2 becomes \
          withTax(x) for static double withTax(double a) { return a * 1.2; }); a void method's \
          statements match the same statements in any block, a local it declares standing for a \
          local the code declares and does not use after them; statements ending in return x \
          match them and the return (return f(...);), or code going on after them \
          (T x = f(...);). Other names must mean what they mean in the body. So near-duplicates \
          become calls: write the method with the differences as parameters, then fold it.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "The method, as Type.method.").required(),
              Arg.integer(
                  "line", "Optional: only matches starting on this line (of in, else file)."),
              Arg.string("in", "Optional, with line: the file whose line it is, if not file.")),
          Kind.REFACTORING,
          Tools::fold);

  static final Def PARAMETERIZE =
      new Def(
          "parameterize",
          """
          Make methods that are the same but for constants calls of one new method that takes \
          those constants as parameters: logEmail(String to) { return String.format("email to \
          %s", to); } and logSms(String addr) { return String.format("sms to %s", addr); } become \
          return logTo(to, "email to %s"); and return logTo(addr, "sms to %s"); of a new private \
          logTo(String to, String format). Each keeps its signature and callers; inline retires \
          them.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the methods."),
              Arg.strings(
                      "functions",
                      "The methods, Type.method or method, of one type with one signature; the"
                          + " new method is the first one's.")
                  .required(),
              Arg.string("name", "Name of the new method.").required(),
              Arg.strings(
                      "parameters",
                      "A name for each constant that differs, in the order they appear in the"
                          + " first method.")
                  .required()),
          Kind.REFACTORING,
          Tools::parameterize);

  static final Def CHANGE_TRY =
      new Def(
          "change_try",
          """
          Change the try that starts on a line, one change a call: add_catch IOException adds \
          catch (IOException e) { throw e; } before any clause of a supertype; remove_resource \
          declares the resource before a try of the body whose finally closes it. A try left with \
          nothing to catch or finish is unwrapped.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "The try's 1-based line.").required(),
              Arg.string("add_catch", "A type to catch."),
              Arg.string("body", "With add_catch: its statements; e is the exception."),
              Arg.string(
                  "join", "With add_catch: a caught type whose clause it joins as a multi-catch."),
              Arg.string("remove_catch", "A caught type to remove."),
              Arg.string("finally", "Statements of a new finally."),
              Arg.bool("remove_finally", "Remove the finally."),
              Arg.bool("unwrap", "Replace the try with its statements."),
              Arg.string("add_resource", "Reader r = open(), or an effectively final variable."),
              Arg.integer(
                  "position",
                  "With add_resource: 1-based, last by default; resources close last first."),
              Arg.string("remove_resource", "The resource's variable."),
              Arg.IMPORTS),
          Kind.EDIT,
          ChangeTry::run);
  static final Def CHECK =
      new Def(
          "check",
          """
          Report where the project stands: its compile errors, and with run_tests the tests that \
          use the changed main files, run through Maven: the test classes changed, and those whose \
          code refers to a type a changed file declares. Alone, the changes are this session's; \
          failures are named by test and line. tests names the tests to run instead (CartTest, \
          CartTest#totals); timeout is the seconds they may take (30). As a step of a batch, \
          {"tool": "check"} is a checkpoint: the code must type-check there (and the tests pass), \
          and when a later step is refused, the steps through it are kept.\
          """,
          List.of(
              Arg.bool("run_tests", "Also run the tests that use the changed files."),
              Arg.strings("tests", "Tests to run in place of those: classes or surefire patterns."),
              Arg.integer("timeout", "Seconds the tests may run before Maven is stopped (30).")),
          Kind.READ,
          (project, args) -> Check.run(project, args));
  static final List<Def> ALL =
      List.of(
          INSPECT,
          RENAME,
          MOVE,
          PULL_UP,
          PUSH_DOWN,
          CREATE_FILE,
          REPLACE_TEXT,
          ADD_DECLARATION,
          REPLACE_DECLARATION,
          ADD_PARAMETER,
          REMOVE_PARAMETER,
          MOVE_PARAMETER,
          EXTRACT_FUNCTION,
          EXTRACT_VARIABLE,
          INLINE,
          ADD_RESULT,
          TYPE_SWITCH_TO_METHOD,
          FOLD,
          PARAMETERIZE,
          CHANGE_TYPE,
          ADD_SUPERTYPE,
          EXTRACT_SUPERTYPE,
          CONVERT_LAMBDA,
          CONVERT_TO_RECORD,
          EXTRACT_TYPE,
          SPLIT,
          FUNCTION_TO_METHOD,
          REPLACE_EXPRESSION,
          INSERT_STATEMENTS,
          REPLACE_STATEMENTS,
          ADD_CASE,
          ADD_ELEMENT,
          CHANGE_TRY,
          MOVE_STATEMENT,
          INVERT_IF,
          LOOP_TO_STREAM,
          CONVERT_LOOP,
          SUBCLASS_TO_STRATEGY,
          CHANGE_SIGNATURE,
          REMOVE_ELSE,
          ENCAPSULATE_FIELD,
          CHECK,
          BATCH,
          UNDO);

  /** move_member to the method's own class, by Go's name for it: an alias, not published. */
  static final Def METHOD_TO_FUNCTION =
      new Def(
          "method_to_function",
          """
          Make an instance method static in its own class, the instance its first parameter: \
          move with to the method's class.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.method, or Type.method(params) for an overload.")
                  .required()),
          Kind.REFACTORING,
          Tools::methodToFunction);

  /**
   * remove_parameter with value, by gorefactor's name for a parameter the method computes: an
   * alias, published only under JREFACTOR_PUBLISH_ALIASES.
   */
  static final Def REPLACE_PARAMETER =
      new Def(
          "replace_parameter",
          """
          Replace a parameter with a value the method computes from its other parameters: \
          remove_parameter with value.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The parameter to remove.").required(),
              Arg.string("value", "What every call passes for it, as the method can compute it.")
                  .required()),
          Kind.REFACTORING,
          (project, args) -> {
            args.required("value");
            return Signatures.removeParameter(project, ByLine.named(project, args));
          });

  /**
   * insert_statements with one build loop, by gorefactor's name: an alias (#376's arm publishes
   * it).
   */
  static final Def CREATE_LOOP =
      new Def(
          "create_loop",
          """
          Insert a loop over what over names, its head written from the code: insert_statements \
          with build [{"loop": over, "name": ..., "form": ..., "type": ..., "body": [...]}].\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("over", "What the loop walks: an array, an Iterable, a Map, or an int.")
                  .required(),
              Arg.string("name", "The loop variable's name."),
              Arg.oneOf("form", "for_each (the default) or index.", "for_each", "index"),
              Arg.string("type", "The elements' type, when the code does not say it."),
              Arg.json(
                  "body",
                  """
                  {
                    "type": "array",
                    "description": "The loop's body: entries as build takes them.",
                    "items": {"anyOf": [{"type": "string"}, {"type": "object"}]}
                  }
                  """),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) -> insertOne(project, args, Map.of("over", "loop")));

  /** insert_statements with one build call or construction, by gorefactor's name: an alias. */
  static final Def CREATE_CALL =
      new Def(
          "create_call",
          """
          Insert a call or a construction written from names: insert_statements with build \
          [{"call": ..., "args": [...], "into": ..., "type": ...}] (or new in place of call).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("call", "The method, with what it is called on."),
              Arg.string("new", "In place of call: the class to construct."),
              Arg.strings("args", "The arguments, in order."),
              Arg.string("into", "The variable the result goes into, or return."),
              Arg.string("type", "The type to declare into with."),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) -> insertOne(project, args, Map.of()));

  /**
   * insert_statements with one build guard, by gorefactor's name: an alias (#376's arm publishes
   * it).
   */
  static final Def CREATE_GUARD =
      new Def(
          "create_guard",
          """
          Insert a guard and its exit: insert_statements with build [{"guard": ..., "exit": ..., \
          "value": ..., "message": ...}] (or guard_null in place of guard).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("guard", "The condition."),
              Arg.string("guard_null", "In place of guard: what must not be null."),
              Arg.string("exit", "return, continue, break, throw, or require for guard_null.")
                  .required(),
              Arg.string("value", "What return returns, or the exception's type for throw."),
              Arg.string("message", "The exception's message, for throw or require."),
              Arg.IMPORTS),
          Kind.EDIT,
          (project, args) -> insertOne(project, args, Map.of()));

  /** extract_variable with as=parameter, by gorefactor's name: an alias, not published. */
  static final Def INTRODUCE_PARAMETER =
      new Def(
          "introduce_parameter",
          """
          Replace an expression in a method's body with a new parameter, each call passing it: \
          extract_variable with as=parameter.\
          """,
          List.of(
              Arg.FILE,
              Arg.string("function", "Instead of line: the method whose body uses it first."),
              Arg.integer("line", "1-based line on which the expression starts."),
              Arg.string("expression", "Source text of the expression.").required(),
              Arg.string("parameter", "Name of the new parameter.").required(),
              Arg.integer("column", "Optional 1-based column where the expression starts.")),
          Kind.REFACTORING,
          GoNames::introduceParameter);

  /** extract_type of every parameter of a method, by gorefactor's name: an alias. */
  static final Def CONVERT_PARAMS_TO_OBJECT =
      new Def(
          "convert_params_to_object",
          """
          Bundle a method's parameters into one record every call builds: extract_type with all \
          of its parameters as members.\
          """,
          List.of(
              Arg.FILE,
              Arg.string("name", "The method: Type.method.").required(),
              Arg.string("type_name", "Optional: the record's name (default <Method>Args).")),
          Kind.REFACTORING,
          GoNames::paramsToObject);

  /** replace_declaration with empty content, by gorefactor's name: an alias. */
  static final Def REMOVE_DEAD_CODE =
      new Def(
          "remove_dead_code",
          """
          Delete a declaration nothing uses: replace_declaration with empty content; also, more \
          to delete with it in one batch.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "Optional: a line of its declaration or a use."),
              Arg.string("name", "Type, Type.member or member.").required(),
              Arg.integer("column", "Optional 1-based column of name."),
              Arg.strings("also", "Optional: more members of file to delete with it."),
              Arg.string("value", "Not built here.")),
          Kind.EDIT,
          GoNames::removeDeadCode);

  /** type_switch_to_method, by gorefactor's name for its first step: an alias. */
  static final Def PREPARE_SWITCH_FOR_TYPES =
      new Def(
          "prepare_switch_for_types",
          """
          Move the cases of a switch over types into a method of each type: \
          type_switch_to_method, with method its name.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line on which the switch starts."),
              Arg.string("function", "Instead of line: the method whose only switch it is."),
              Arg.string("interface", "The switch's type, which declares the method."),
              Arg.string("method", "The new method's name.").required()),
          Kind.REFACTORING,
          GoNames::prepareSwitch);

  /**
   * Names {@code batch} and the CLI still accept for a tool published under its shared name
   * (gorefactor's {@code docs/parity.md}): extract_method for extract_function, Go's
   * method_to_function for move_member in place, extract_struct for extract_type, Go's and
   * TypeScript's old extract_interface for extract_supertype, replace_parameter for
   * remove_parameter with value, create_loop, create_call and create_guard for insert_statements
   * with one build entry; and gorefactor's introduce_parameter, convert_params_to_object,
   * remove_dead_code and prepare_switch_for_types ({@link GoNames}, issue #372).
   */
  static final Map<String, Def> ALIASES =
      Map.ofEntries(
          Map.entry("extract_method", EXTRACT_FUNCTION),
          Map.entry("method_to_function", METHOD_TO_FUNCTION),
          Map.entry("move_type", MOVE_TYPE),
          Map.entry("move_member", MOVE_MEMBER),
          Map.entry("move_file", MOVE_FILE),
          Map.entry("inline_function", INLINE_FUNCTION),
          Map.entry("inline_variable", INLINE_VARIABLE),
          Map.entry("extract_struct", EXTRACT_TYPE),
          Map.entry("extract_interface", EXTRACT_SUPERTYPE),
          Map.entry("replace_parameter", REPLACE_PARAMETER),
          Map.entry("create_loop", CREATE_LOOP),
          Map.entry("create_call", CREATE_CALL),
          Map.entry("create_guard", CREATE_GUARD),
          Map.entry("introduce_parameter", INTRODUCE_PARAMETER),
          Map.entry("convert_params_to_object", CONVERT_PARAMS_TO_OBJECT),
          Map.entry("remove_dead_code", REMOVE_DEAD_CODE),
          Map.entry("prepare_switch_for_types", PREPARE_SWITCH_FOR_TYPES));

  /**
   * Argument names a tool still accepts under its published ones, by tool: extract_type's
   * parameters and fields became members, parameter and field became as.
   */
  static final Map<String, Map<String, String>> ARG_ALIASES =
      Map.of(
          "extract_type",
          Map.of(
              "parameters",
              "members",
              "fields",
              "members",
              "parameter",
              "as",
              "field",
              "as",
              "struct",
              "new_type"));

  private static Outcome methodToFunction(SourceProject project, Args args) throws Exception {
    String name = args.required("name");
    Qualified q = Qualified.parse(name);
    if (q.type() == null || q.type().isEmpty()) {
      throw new Refusal(
          "method_to_function takes the method as Type.method, so it knows the class to keep it in.");
    }
    return MoveMember.run(project, args.required("file"), name, q.type());
  }

  private static Outcome inspect(SourceProject project, Args args) throws Exception {
    String pattern = args.string("pattern");
    Integer start = args.integer("start_line");
    Integer end = args.integer("end_line");
    List<String> names = args.list("names");
    List<String> uses = args.list("uses");
    List<Map<String, Object>> targets = args.objects("targets");
    if (!targets.isEmpty()) {
      if (args.string("file") != null
          || args.string("function") != null
          || start != null
          || end != null) {
        throw new Refusal(
            "give either targets or file (with function or start_line/end_line), not both.");
      }
      List<String> parts = new ArrayList<>();
      for (int i = 0; i < targets.size(); i++) {
        Map<String, Object> t = targets.get(i);
        String part;
        try {
          if (!Set.of("file", "function", "start_line", "end_line").containsAll(t.keySet())) {
            throw new Refusal(
                "a target takes file, with function or start_line/end_line; got "
                    + t.keySet()
                    + ".");
          }
          part = inspect(project, new Args("inspect", t, INSPECT.arguments())).text();
        } catch (Refusal r) {
          part = "target " + (i + 1) + ": " + r.getMessage() + "\n";
        }
        parts.add(part);
      }
      return Outcome.of(String.join("\n", parts));
    }
    String function = args.string("function");
    if (function != null) {
      if (start != null || end != null || pattern != null || !names.isEmpty() || !uses.isEmpty()) {
        throw new Refusal("function shows one method of file; leave out the other arguments.");
      }
      return Outcome.of(Inspect.function(project, args.required("file"), function));
    }
    if (!uses.isEmpty()) {
      if (!names.isEmpty()
          || pattern != null
          || start != null
          || end != null
          || args.string("file") != null) {
        throw new Refusal(
            "give uses alone: each is found in the project by its name, without file, names,"
                + " pattern, start_line or end_line.");
      }
      return Outcome.of(Uses.of(project, uses));
    }
    if (!names.isEmpty()) {
      if (pattern != null || start != null || end != null || args.string("file") != null) {
        throw new Refusal(
            "names finds each declaration anywhere in the project; leave out file, pattern,"
                + " start_line and end_line, or names.");
      }
      return Outcome.of(Inspect.names(project, names, null));
    }
    if (pattern != null) {
      if (start != null || end != null) {
        throw new Refusal(
            "pattern searches all of a file (or, with no file, the project); leave out"
                + " start_line and end_line, or the pattern.");
      }
      return Outcome.of(Inspect.pattern(project, args.string("file"), pattern));
    }
    if (args.string("file") == null && start == null && end == null) {
      return Outcome.of(Inspect.overview(project));
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
      return Rename.run(project, file, name, newName);
    }
    return Rename.run(project, file, line, name, args.integer("column"), newName);
  }

  private static Outcome moveType(SourceProject project, Args args) throws Exception {
    if (args.has("package")) {
      return MoveToPackage.run(
          project, args.required("file"), args.required("name"), args.required("package"));
    }
    if (args.has("to")) {
      return NestType.run(
          project, args.required("file"), args.required("name"), args.required("to"));
    }
    return MoveType.run(project, args.required("file"), args.required("name"));
  }

  private static Outcome extractVariable(SourceProject project, Args args) throws Exception {
    Integer line = args.integer("line");
    return ExtractVariable.run(
        project,
        args.required("file"),
        line,
        args.required("expression"),
        args.required("name"),
        args.integer("column"),
        args.string("as"),
        args.flag("all_occurrences"));
  }

  private static Outcome inlineVariable(SourceProject project, Args args) throws Exception {
    Integer line = args.integer("line");
    return InlineVariable.run(
        project, args.required("file"), line, args.required("name"), args.integer("column"));
  }

  private static Outcome inlineFunction(SourceProject project, Args args) throws Exception {
    return InlineFunction.run(project, args.required("file"), args.required("name"), null, false);
  }

  private static Outcome changeType(SourceProject project, Args args) throws Exception {
    if (args.has("type_parameter")) {
      return TypeParameters.bound(project, args.has("line") ? ByLine.named(project, args) : args);
    }
    if (args.has("parameter") || args.flag("return_type")) {
      return Signatures.changeType(project, ByLine.named(project, args));
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
        args.flag("names_only"),
        args.flag("past_try"));
  }

  private static Outcome invertIf(SourceProject project, Args args) throws Exception {
    return InvertIf.run(project, args.required("file"), args.integer("line"));
  }

  private static Outcome removeElse(SourceProject project, Args args) throws Exception {
    return RemoveElse.run(project, args.required("file"), args.integer("line"));
  }

  private static Outcome moveMember(SourceProject project, Args args) throws Exception {
    return MoveMember.run(
        project,
        args.required("file"),
        args.required("name"),
        args.required("to"),
        args.list("imports"));
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
    if (ALIASES.containsKey(bare)) {
      return ALIASES.get(bare);
    }
    throw noTool(name);
  }

  /** The refusal for a tool name there is none of, listing the tools there are. */
  private static Refusal noTool(String name) {
    return new Refusal(
        "No tool "
            + name
            + ". The tools are "
            + String.join(", ", ALL.stream().map(Def::name).toList())
            + ".");
  }

  /** A tool's name as an MCP client gives it: mcp__, the server's name, __, the tool's. */
  private static final Pattern CLIENT_NAME = Pattern.compile("mcp__.+?__(\\w+)");

  /**
   * Runs one call of {@code def}: its arguments checked, then its handler, as one change in the
   * journal, so {@code undo} can revert it; a call that fails leaves nothing changed.
   */
  static Outcome call(Def def, SourceProject project, Map<String, Object> values) throws Exception {
    Args args =
        new Args(def.name(), values, def.arguments())
            .renamed(ARG_ALIASES.getOrDefault(def.name(), Map.of()))
            .without(RETIRED_ARGS.getOrDefault(def.name(), Set.of()));
    args.check(def.arguments());
    Journal.begin(project, def.name());
    try (Session.Scope scope =
        project
            .session()
            .scope(
                def.kind() == Kind.EDIT,
                def.kind() == Kind.REFACTORING && args.flag(Exceptional.FLAG))) {
      try {
        Outcome outcome = def.handler().run(project, args).withExceptional(scope.reports());
        Journal.commit(project);
        scope.keep();
        return outcome;
      } catch (Refusal.Kept kept) {
        Journal.commit(project); // what the call kept, which it has put back as it was
        throw kept;
      } catch (Exception | Error e) {
        abort(project, e);
        throw e;
      }
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

  /**
   * Arguments a tool still accepts and ignores, by tool: add_declaration places members by kind, so
   * after and before no longer choose where (the user, 2026-11-07).
   */
  static final Map<String, Set<String>> RETIRED_ARGS =
      Map.of("add_declaration", Set.of("after", "before"));

  /** replace_expression as it was before build (#165): typed text only, for JREFACTOR_NO_BUILD. */
  static final Def REPLACE_EXPRESSION_TYPED =
      new Def(
          "replace_expression",
          """
          Replace one expression with new text. Find it by file, the line it starts on \
          and its exact source text (column when the text occurs twice on the line), as \
          extract_variable does.\
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

  /** insert_statements as it was before build (#149): typed text only, for JREFACTOR_NO_BUILD. */
  static final Def INSERT_STATEMENTS_TYPED =
      new Def(
          "insert_statements",
          """
          Insert statements before or after the statement that starts on a line (directly in a \
          block). content is the statements, at any indentation; they are indented as \
          that statement is.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("content", "The statements.").required(),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::insertStatements);

  /**
   * replace_statements as it was before build (#149) and surround's loop object (#163): typed text
   * only, for JREFACTOR_NO_BUILD.
   */
  static final Def REPLACE_STATEMENTS_TYPED =
      new Def(
          "replace_statements",
          """
          Replace whole statements of one block, from the one starting on start_line to the one \
          ending on end_line, with content (statements, re-indented); empty content deletes them.
          With surround in place of content (try, if (ready), for (Item i : items), while (more), \
          synchronized (lock)), the statements go inside that block; a try catches and rethrows \
          the checked exceptions they throw.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "1-based line the first statement starts on.").required(),
              Arg.integer("end_line", "1-based line the last statement ends on.").required(),
              Arg.string("content", "The new statements; empty to delete."),
              Arg.string(
                  "surround",
                  "In place of content: the head of a block to put them in (try, if (cond), ...)."),
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::replaceStatements);

  /** The statement edits as a server without build publishes them, by name. */
  static final Map<String, Def> TYPED =
      Map.of(
          "insert_statements", INSERT_STATEMENTS_TYPED,
          "replace_statements", REPLACE_STATEMENTS_TYPED,
          "replace_expression", REPLACE_EXPRESSION_TYPED);

  /** The aliases that are one build entry, which a server without build does not have. */
  static final Set<String> BUILDING_ALIASES = Set.of("create_loop", "create_call", "create_guard");

  /** gorefactor's names for the generators, published under JREFACTOR_PUBLISH_ALIASES. */
  static final List<Def> PUBLISHABLE_ALIASES =
      List.of(CREATE_LOOP, CREATE_CALL, CREATE_GUARD, REPLACE_PARAMETER);

  /** The arguments of insert_statements that say where, which the create aliases pass as given. */
  private static final Set<String> PLACEMENT = Set.of("file", "line", "where", "imports");

  private static Outcome loopToStream(SourceProject project, Args args) throws Exception {
    return LoopToStream.run(project, args.required("file"), args.integer("line"));
  }

  private static Outcome convertLoop(SourceProject project, Args args) throws Exception {
    return ConvertLoop.run(
        project, args.required("file"), args.integer("line"), args.required("to"));
  }

  private static Outcome subclassToStrategy(SourceProject project, Args args) throws Exception {
    return SubclassToStrategy.run(
        project,
        args.required("file"),
        args.required("name"),
        args.required("method"),
        args.string("type"));
  }

  /**
   * insert_statements with one build entry: every argument but the placement ones, under its name
   * or the one {@code renames} gives it, and the placement arguments as given.
   */
  private static Outcome insertOne(SourceProject project, Args args, Map<String, String> renames)
      throws Exception {
    Map<String, Object> entry =
        new LinkedHashMap<>(args.without(PLACEMENT).renamed(renames).values());
    return Primitives.insertStatements(
        project, args.routed(args.tool(), PLACEMENT).with("build", List.of(entry)));
  }

  /**
   * The tools the server publishes: every tool, under JREFACTOR_NO_BUILD with the typed edits, and
   * under JREFACTOR_PUBLISH_ALIASES gorefactor's names for the generators after them.
   */
  static List<Def> published(Session session) {
    List<Def> tools =
        new ArrayList<>(
            session.noBuild
                ? ALL.stream().map(def -> TYPED.getOrDefault(def.name(), def)).toList()
                : ALL);
    if (session.publishAliases) {
      for (Def alias : PUBLISHABLE_ALIASES) {
        if (!(session.noBuild && BUILDING_ALIASES.contains(alias.name()))) {
          tools.add(alias);
        }
      }
    }
    return tools;
  }

  /** The tool named {@code name} as {@code session}'s server publishes it, or a refusal. */
  static Def named(String name, Session session) {
    Def def = named(name);
    if (!session.noBuild) {
      return def;
    }
    if (BUILDING_ALIASES.contains(def.name())) {
      throw noTool(name);
    }
    return TYPED.getOrDefault(def.name(), def);
  }

  private static Outcome parameterize(SourceProject project, Args args) throws Exception {
    return Parameterize.run(
        project,
        args.required("file"),
        args.list("functions"),
        args.required("name"),
        args.list("parameters"));
  }

  /** Whether {@code name} (Outer.Inner) names a member type of {@code rel}, by syntax. */
  private static boolean memberType(SourceProject project, String rel, String name)
      throws IOException {
    if (!name.contains(".") || name.contains("(")) {
      return false;
    }
    CompilationUnit unit = Jdt.parseSyntax(Edits.textOf(Map.of(), project.javaFile(rel)));
    return Declarations.typePath(unit, name.split("\\.")) != null;
  }
}
