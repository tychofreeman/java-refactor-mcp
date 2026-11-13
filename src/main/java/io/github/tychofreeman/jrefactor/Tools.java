package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
                "Optional: true to refuse a change to where or whether an exception is thrown.");
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
          - no arguments: the source roots, their packages and files with line counts.
          - file: the file with method, constructor and initializer bodies elided: declarations, \
          fields, signatures and comments in full, and in place of each body of two lines or more \
          the lines it takes.
          - file + start_line/end_line: those lines (end_line defaults to the end of the file).
          - pattern (a Java regular expression), with file or without: the lines that match, \
          numbered, file by file over the project's Java files (at most 100): where a name is \
          used, which line to edit. Use it rather than grep.
          - names, alone: declarations by name, wherever they are: ["Fold"] the class with its \
          bodies elided, ["Fold.leave", "Cart.total"] each method's overloads or field in full.\
          """,
          List.of(
              Arg.string(
                  "file", "Java file path; optional with pattern, to search" + " every Java file."),
              Arg.integer("start_line", "First line to show (1-based)."),
              Arg.integer("end_line", "Last line to show (inclusive)."),
              Arg.string(
                  "pattern", "Optional: show the lines that match this Java regular expression."),
              Arg.strings(
                  "names",
                  "Optional, alone: read declarations by name, anywhere in the project: Type (its"
                      + " outline) or Type.member (each overload of a method, a field, a nested"
                      + " type).")),
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
          Returns the lines changed in each file.\
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
          to, instead of a local; it must then be literals and constants only, or, as a \
          parameter, also the method's parameters: each call passes it with its own arguments in \
          their place (qty / 10 becomes bonus, and total(n + 1, 3) passes (n + 1) / 10).\
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
                      + "constants (as a parameter, also the method's parameters).",
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
          Change the declared type of a field, so that its uses must still compile: the field \
          Price.total of type int becomes long. Identify the field as Type.field, a top-level \
          class of the file.
          A method's parameter (parameter) or return type (return_type=true) changes in it and \
          every override and implementation, and in lambdas that write the parameter's type; \
          calls are not edited. The method may be found by line in place of name, as \
          add_parameter finds it.\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares " + "the field."),
              Arg.string(
                  "name",
                  "Type.field, the field whose type to change; or Type.method (with "
                      + "parameter types for an overload) with parameter or return_type."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
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

  static final Def CHANGE_SIGNATURE =
      new Def(
          "change_signature",
          """
          Change a method's return type or the exceptions it declares, in it, in every override \
          and implementation, and in each method a method reference implements it with: \
          return_type=void and add_throws=IOException turn boolean apply(Rename r) into void \
          apply(Rename r) throws IOException. For void, return x; becomes return; and a lambda \
          r -> true becomes r -> {}, a returned call kept as a statement. remove_throws drops \
          an exception from each throws clause. A new checked exception is carried to the \
          callers: each calling method declares it too, and its callers after it, up to a \
          method named by stop_at or one that cannot declare it (run() of a Runnable), where \
          the call is caught and rethrown unchecked, in a new try or as one more catch of the \
          try it is in. Calls are not edited otherwise: change one that uses the result in the \
          same batch. The method may be found by line in place of name, as add_parameter \
          finds it.\
          """,
          List.of(
              Arg.FILE.described("Java file path; the file that declares the method."),
              Arg.string(
                  "name", "Type.method (with parameter types for an overload), e.g. Move.apply."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("return_type", "Optional: the new return type as written, or void."),
              Arg.string(
                  "add_throws", "Optional: exception types to declare, separated by commas."),
              Arg.string(
                  "remove_throws",
                  "Optional: exception types to stop declaring, separated by commas."),
              Arg.string(
                  "callers",
                  "Optional, with add_throws: propagate (the default: each calling method"
                      + " declares the exception too, up the chain), catch (each call is caught"
                      + " and rethrown unchecked) or refuse (a call that does not handle it is"
                      + " refused)."),
              Arg.string(
                  "stop_at",
                  "Optional, with callers=propagate: Type.method names, separated by commas,"
                      + " where propagating ends in a catch."),
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
          Group members of a method, class or record into a new type in its own file, every use \
          following; what is grouped follows from what name is.
          - A method's parameters: name Shipping.cost, members [width, \
          height], new_type Size, as size: cost(int weight, int width, int height) becomes \
          cost(int weight, Size size), its body reads size.width(), and calls pass new Size(3, 4).
          - A class's fields: name Person, members [street, city], new_type Address, as home: the \
          fields move into a class Address that Person holds in home, and uses become home.street.
          - A record's components: name Order, members [street, city], new_type Address, as \
          address: new Order(id, s, c) becomes new Order(id, new Address(s, c)), and o.street() \
          becomes o.address().street(); value_semantics=true accepts the changed toString.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file that declares the method, class or record, relative to the project"
                      + " root."),
              Arg.string(
                      "name",
                      "The method (Type.member, with (parameter types) for an overload), or a"
                          + " top-level class or record of file.")
                  .required(),
              Arg.strings(
                      "members",
                      "What to group: the method's parameters or the record's components (next to"
                          + " each other), or the class's fields.")
                  .required(),
              Arg.string("new_type", "The new type's name.").required(),
              Arg.string("as", "The name of the parameter, field or component that holds it.")
                  .required(),
              Arg.bool("value_semantics", "For a record: accept its changed generated toString."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          ExtractType::run);

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

  static final Def INSERT_STATEMENTS =
      new Def(
          "insert_statements",
          """
          Insert statements before or after the statement that starts on a line (directly in a \
          block). content is the statements, at any indentation; they are indented as \
          that statement is.
          With build in place of content, the statements are written from names and their types \
          are read from the code: [{"call": "pricing.priceOf", "args": ["order", "customer"], \
          "into": "price"}] writes Money price = pricing.priceOf(order, customer); (into a \
          variable that is there assigns it, into return returns it), {"new": "ArrayList", \
          "into": "open", "type": "List<Order>"} writes List<Order> open = new ArrayList<>(); \
          and a string entry is a statement as typed. The result names each type it wrote.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line a statement starts on.").required(),
              Arg.oneOf("where", "Before or after it (default after).", "before", "after"),
              Arg.string("content", "The statements."),
              Arg.BUILD,
              Arg.IMPORTS),
          Kind.EDIT,
          Primitives::insertStatements);

  static final Def REPLACE_STATEMENTS =
      new Def(
          "replace_statements",
          """
          Replace whole statements of one block, from the one starting on start_line to the one \
          ending on end_line, with content (statements, re-indented); empty content deletes them.
          With surround in place of content (try, if (ready), for (Item i : items), while (more), \
          synchronized (lock)), the statements go inside that block; a try catches and rethrows \
          the checked exceptions they throw.
          With build in place of content, the new statements are written from names, as \
          insert_statements does.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "1-based line the first statement starts on.").required(),
              Arg.integer("end_line", "1-based line the last statement ends on.").required(),
              Arg.string("content", "The new statements; empty to delete."),
              Arg.BUILD,
              Arg.string(
                  "surround",
                  "In place of content: the head of a block to put them in (try, if (cond), ...)."),
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
          The two statements must be independent. Strict (the default): they share no name that \
          one of them writes, and at most one of them has a side effect (a call, an assignment, a \
          new object). names_only=true relaxes that to the shared-name rule only, so two calls can \
          swap order: pass it only when the order of the calls does not matter.
          A return, break, continue, throw or yield never moves, and neither a statement with a \
          side effect nor one that may throw moves past one that may leave the block early (an \
          if that returns), nor a call past a read of what it could change. past_try=true moves \
          a statement past a try next to it though their effects are ordered (they still share \
          no written name): below the try it then runs only when the try completes normally, \
          and the result says so.\
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
          Rewrite a loop as another statement form that runs the same code in the same order: \
          to=for_each turns an index for over a list or an array, for (int i = 0; i < \
          names.size(); i++) { print(names.get(i)); }, into for (String name : names) { \
          print(name); }, and a for over an iterator whose body starts with T e = it.next() \
          into for (T e : source); to=iterator and to=for turn an enhanced for into a for over \
          its iterator, or over the index of a list or an array, the element read first in the \
          body. to=while writes a for's init before a while and its update last in the body and \
          before each continue; to=for turns a while back into a for, the local declared just \
          before it and the body's last assignment taking their places. to=while turns do { body \
          } while (c); into while (true) { body if (!c) break; }, and to=do_while a while into \
          if (c) { do { body } while (c); }. Identify the loop by the line it starts on.\
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
                      + "(same source root).")),
          Kind.REFACTORING,
          Tools::moveType);

  static final Def PULL_UP =
      new Def(
          "pull_up",
          """
          Move a method or field from its class to its direct superclass, or to a direct supertype named by \
          to: the member leaves Sub and is added to Base, a field after its fields and a method at its \
          end. Every call still compiles, since Sub inherits the method. name is Sub.method.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
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
          Sub and is added to each subclass, a field after its fields and a method at its end. name is \
          Sub.method.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the class."),
              Arg.string("name", "The class and the method, as Sub.method.").required()),
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
          receiver, calls the method and ends in fail; type the assertion in place of fail.
          When the server runs skeleton-only (JREFACTOR_SKELETON_ONLY=1), Java content may hold \
          only a type's header: start from declaration or test_for, and add members after.\
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
          Move a type, a member or a file, and update every reference to it. What moves follows \
          from the arguments:
          - name Outer.Inner: the static nested type to its own file in the package (Shop.Line \
          becomes Line.java; Shop.Line becomes Line everywhere).
          - name Type with package: the file's only top-level type to that package of its source \
          root; imports follow, package-private declarations used across become public.
          - name Type.member with to: a method or a static field to the class to, in this file \
          or another (to=Tax, or com.example.Tax); an instance method becomes static there with \
          the instance as its first parameter, receiver, and every call passes it. To its own \
          class, a method is made static in place.
          - no name, to a path: the file or directory, not Java under a source root (copy keeps \
          it; delete=true without to removes it).\
          """,
          List.of(
              Arg.FILE.described(
                  "The file that declares what moves, or, without name, the file or directory to"
                      + " move."),
              Arg.string(
                  "name",
                  "What moves: Outer.Inner, Type (with package), or Type.member (with to); omit it"
                      + " to move the file itself."),
              Arg.string(
                  "to",
                  "With Type.member, the class it moves to; without name, the file's new path."),
              Arg.string("package", "With name Type: the package it moves to."),
              Arg.bool("copy", "Without name: copy the file, keeping the original."),
              Arg.bool("delete", "Without name: delete the file instead (no to)."),
              Arg.IMPORTS),
          Kind.REFACTORING,
          Tools::move);

  private static Outcome move(SourceProject project, Args args) throws Exception {
    String name = args.string("name");
    String to = args.string("to");
    String pkg = args.string("package");
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("file", args.required("file"));
    Def target;
    if (name == null) {
      if (pkg != null) {
        throw new Refusal("package moves a type: give name, the file's top-level type.");
      }
      target = MOVE_FILE;
      if (to != null) {
        values.put("to", to);
      }
      if (args.flag("copy")) {
        values.put("copy", true);
      }
      if (args.flag("delete")) {
        values.put("delete", true);
      }
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
      values.put("name", name);
      if (to != null) {
        target = MOVE_MEMBER;
        values.put("to", to);
        if (!args.list("imports").isEmpty()) {
          values.put("imports", args.list("imports"));
        }
      } else {
        target = MOVE_TYPE;
        if (pkg != null) {
          values.put("package", pkg);
        }
      }
      if (args.flag(Exceptional.FLAG)) {
        values.put(Exceptional.FLAG, true);
      }
    }
    if (target != MOVE_MEMBER && !args.list("imports").isEmpty()) {
      throw new Refusal(
          "imports is for moving a member (name Type.member with to), whose import fix takes it.");
    }
    Args routed = new Args(target.name(), values);
    routed.check(target.args());
    return target.handler().run(project, routed);
  }

  /**
   * One tool for the inverses of extract (gorefactor's {@code docs/parity.md}): it routes to
   * inline_variable or inline_function by its arguments, which stay names batch and the CLI accept.
   */
  static final Def INLINE =
      new Def(
          "inline",
          """
          Inline what name names into where it is used, and delete it:
          - with line, the local variable name declared or used there: its initializer replaces \
          every use (int subtotal = price * qty; return subtotal + tax; becomes return (price * \
          qty) + tax;); column when the line has two of that name.
          - without line, name Type.method: every call of the static method becomes its body, \
          the arguments in place of the parameters, and the method is deleted (return a + \
          tax(a) with static double tax(double x) { return x * 0.2; } becomes return a + (a * \
          0.2);); an argument whose effects would run in another order is bound to a local first.
          - with field, name a class (Person) and field its field (home) holding a class only it \
          uses (Address): Address's members take the field's place, home.street becomes street, \
          and Address.java is deleted.\
          """,
          List.of(
              Arg.FILE.described(
                  "Java file path: the variable's, or the file that" + " declares the method."),
              Arg.string("name", "The variable, or Type.method for a static method.").required(),
              Arg.integer("line", "For a variable: a 1-based line it is declared or used on."),
              Arg.integer("column", "Optional 1-based column of the name, when the line has two."),
              Arg.string(
                  "field",
                  "With name a class: its field whose class, used by nothing else, folds into it.")),
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
          Turn a switch over a value's type into a method on each type: with line the switch \
          expression starts on and name area, return switch (s) { case Circle c -> Math.PI * \
          c.r() * c.r(); case Square q -> q.side() * q.side(); } becomes return s.area();, the \
          interface or abstract class Shape declares double area();, and Circle and Square each \
          get @Override public double area() returning their case's expression, the pattern \
          variable as the receiver (c.r() becomes r()).
          A switch statement of arrow cases, or an if chain of s instanceof T v, makes a void \
          method of each case's statements; a default (or final else) becomes Shape's own \
          implementation (a default method of an interface).\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("line", "1-based line the switch, or the first if, starts on.")
                  .required(),
              Arg.string("name", "The new method's name.").required()),
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
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("file", args.required("file"));
    values.put("name", name);
    Def target;
    if (args.integer("line") != null) {
      target = INLINE_VARIABLE;
      values.put("line", args.integer("line"));
      if (args.integer("column") != null) {
        values.put("column", args.integer("column"));
      }
    } else if (name.contains(".")) {
      if (args.integer("column") != null) {
        throw new Refusal("column is for a variable, with line; a method is named Type.method.");
      }
      target = INLINE_FUNCTION;
    } else {
      throw new Refusal(
          "give line for the local variable "
              + name
              + " (a line it is declared or used on), or name a static method as Type."
              + name
              + " to inline every call of it.");
    }
    if (args.flag(Exceptional.FLAG)) {
      values.put(Exceptional.FLAG, true);
    }
    Args routed = new Args(target.name(), values);
    routed.check(target.args());
    return target.handler().run(project, routed);
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
          old, adds new at the end of the file (a friction-log entry, a CI job).\
          """,
          List.of(
              Arg.FILE.described("Path of the file."),
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
          Add members (methods, fields, constructors, nested types) to a type (the file's only \
          top-level one unless type names it): fields after its fields, constructors after its \
          constructors or fields, the rest at its end; content is the members at any \
          indentation. A member with a stub's signature replaces the stub, and a constructor \
          replaces one of its parameter types that only assigns fields.
          In place of content: test_for (Cart.total) writes a red test of that method; implement \
          (* for every abstract method left, or a supertype's method such as toString) writes \
          stubs, or overrides that call super; constructor (field names, in order) writes a \
          constructor that assigns them.\
          """,
          List.of(
              Arg.FILE,
              Arg.string(
                  "content", "Java member declarations (or test_for or implement in their place)."),
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
          several overloads, add its parameter types: Cart.add(int, String).
          The old Javadoc stays unless content has its own.\
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
          With type_parameter (T, or T extends Comparable<T>) in place of parameter and type, a \
          class gets a type parameter, and value is the type argument every use passes: class \
          Box becomes class Box<T>, and Box b = new Box(x) becomes Box<String> b = new Box<>(x). \
          Inside the class's own instance code a use becomes Box<T>. before names the type \
          parameter to put it ahead of.
          name finds the method as replace_declaration does (Type.member, member, m(int, String) \
          for an overload; a constructor by its type's name); or, in place of name, line finds it \
          from its declaration or a call on that line of file, in any file (column picks one of two \
          calls). value is a Java expression every call can see, with no side effects. before puts the parameter ahead of that one \
          (default: last).
          With name a record (Line) whose canonical constructor is implicit or compact, it adds a \
          component, and every new Line(...) passes value.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The new parameter's name; needed unless type_parameter."),
              Arg.string("type", "Its Java type; needed unless type_parameter."),
              Arg.string(
                  "type_parameter",
                  "Instead of parameter and type: a type parameter as written, with its bound,"
                      + " for the class name names."),
              Arg.string(
                      "value",
                      "The Java expression each call passes; with type_parameter, the type"
                          + " argument each use passes.")
                  .required(),
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
          Remove a parameter no body reads, with its @param tag, and its argument at every call \
          in the project: removing retries, void send(String to, int retries) becomes \
          void send(String to), and send(addr, 3) becomes send(addr). Every declaration it \
          overrides or that overrides it changes too, and every lambda that implements it. Use \
          it last when taking a feature out: \
          first change the bodies so they no longer read it.
          name finds the method as replace_declaration does, or line as add_parameter's does. A \
          dropped argument must have no side effects (a name, a literal, this, a field). With \
          value, a parameter every call passes the same field or literal for is removed though \
          the body reads it: void report(PrintStream out, String r) called as report(out, r) \
          becomes void report(String r), its body reading the field out. value may also be an \
          expression of the other parameters, which every call passes with its own arguments in \
          their place: with value width * height, boolean fits(int width, int height, int area) \
          called as fits(w, h + 1, w * (h + 1)) becomes boolean fits(int width, int height), \
          called as fits(w, h + 1), its body starting with int area = width * height;.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.member, member, or with (parameter types) for an overload."),
              Arg.METHOD_LINE,
              Arg.METHOD_COLUMN,
              Arg.string("parameter", "The parameter to remove.").required(),
              Arg.string(
                  "value",
                  "Optional: what every call passes for it, a field (out, this.out, Config.OUT)"
                      + " or a literal, which the body then reads, or an expression of the"
                      + " other parameters, fields, constants and literals, calls too (width * height, order.customer()),"
                      + " which the body then starts by computing.")),
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
          Move whole statements into a new method, private by default, declared after the one they are in, \
          and call it in their place: lines 9-12 of print, which sum values into total, become \
          int total = sum(values); and a method int sum(List<Integer> values). Behavior is \
          unchanged.
          start_line and end_line (inclusive, as inspect numbers them) cover whole statements \
          of one block; comments and blank lines may be in the range. The locals they use \
          become parameters; the one they set or declare that later code uses is returned, \
          and several are returned in a record named after the method (sum and count as \
          TotalsResult, taken out after the call: int sum = totalsResult.sum();). \
          The method is static when the enclosing one is, and declares the checked exceptions \
          the statements throw. doc becomes its Javadoc. visibility makes it package, protected \
          or public; then it may override a supertype's method of its name and parameter types, \
          as a template method's step, and is marked @Override.\
          """,
          List.of(
              Arg.FILE,
              Arg.integer("start_line", "First line of the statements (1-based).").required(),
              Arg.integer("end_line", "Last line of the statements (inclusive).").required(),
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
          Run several refactorings and edits in order as one change, planned from one read. Each \
          step is {"tool": ..., "args": {...}, "id": optional}: a tool (rename, or as the client \
          names it, mcp__jrefactor__rename) and the arguments it takes alone.
          Give line numbers as in the code you read before the batch: the batch moves them past \
          earlier steps. A line an earlier step rewrote cannot be given by number; name a line \
          an earlier step changed instead: "$prev.start", "$prev.end", "$2.end", "$id.start+1".
          The steps apply together or not at all. check \
          "end" type-checks once, after the last step, so edits may pass through code that does \
          not compile yet (a signature, then its callers). One undo reverts the whole batch.
          After a refusal, retry: true resends that batch with only the steps that change: \
          {"step": 3, "args": {...}} replaces step 3 (its tool too, if the step names one), \
          {"after": 2, "tool": ..., "args": {...}} adds a step after step 2, {"remove": 2} \
          drops step 2: remove and after together move a step.\
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
              Arg.bool(
                  "retry",
                  "Resend the last refused batch with only these steps changed: step N replaces"
                      + " step N, after N adds one after it, remove N drops it.")),
          Kind.CONTROL,
          (project, args) -> Batch.run(project, args));

  static final Def FOLD =
      new Def(
          "fold",
          """
          Replace code that does what an existing method does with calls of it: every match of \
          the method's body in the project, its parameters standing for any expressions of their \
          types, becomes a call. The method stays. An instance method's body matches code in the \
          instance members of its class and subclasses, where this is the same object, and becomes \
          total(...) there. extract_function makes a method from \
          one place; fold reuses one, written or extracted, everywhere else. Two methods with \
          the same body merge as a batch: fold the one to keep, then inline the other.
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
          a private method called from another class.\
          """,
          List.of(
              Arg.FILE.described("Java file that declares the method."),
              Arg.string("name", "Type.method: the method whose body to fold.").required(),
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
          CHANGE_TYPE,
          ADD_SUPERTYPE,
          EXTRACT_SUPERTYPE,
          CONVERT_LAMBDA,
          CONVERT_TO_RECORD,
          EXTRACT_TYPE,
          FUNCTION_TO_METHOD,
          REPLACE_EXPRESSION,
          INSERT_STATEMENTS,
          REPLACE_STATEMENTS,
          ADD_CASE,
          ADD_ELEMENT,
          MOVE_STATEMENT,
          INVERT_IF,
          LOOP_TO_STREAM,
          CONVERT_LOOP,
          SUBCLASS_TO_STRATEGY,
          CHANGE_SIGNATURE,
          REMOVE_ELSE,
          ENCAPSULATE_FIELD,
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
   * Names {@code batch} and the CLI still accept for a tool published under its shared name
   * (gorefactor's {@code docs/parity.md}): extract_method for extract_function, Go's
   * method_to_function for move_member in place, extract_struct for extract_type, Go's and
   * TypeScript's old extract_interface for extract_supertype.
   */
  static final Map<String, Def> ALIASES =
      Map.of(
          "extract_method",
          EXTRACT_FUNCTION,
          "method_to_function",
          METHOD_TO_FUNCTION,
          "move_type",
          MOVE_TYPE,
          "move_member",
          MOVE_MEMBER,
          "move_file",
          MOVE_FILE,
          "inline_function",
          INLINE_FUNCTION,
          "inline_variable",
          INLINE_VARIABLE,
          "extract_struct",
          EXTRACT_TYPE,
          "extract_interface",
          EXTRACT_SUPERTYPE);

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
    int dot = name.indexOf('.');
    if (dot <= 0) {
      throw new Refusal(
          "method_to_function takes the method as Type.method, so it knows the class to keep it in.");
    }
    return MoveMember.run(project, args.required("file"), name, name.substring(0, dot));
  }

  private static Outcome inspect(SourceProject project, Args args) throws Exception {
    String pattern = args.string("pattern");
    Integer start = args.integer("start_line");
    Integer end = args.integer("end_line");
    List<String> names = args.list("names");
    if (!names.isEmpty()) {
      if (pattern != null || start != null || end != null || args.string("file") != null) {
        throw new Refusal(
            "names finds each declaration anywhere in the project; leave out file, pattern,"
                + " start_line and end_line, or names.");
      }
      return Outcome.of(Inspect.names(project, names));
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
      throw new Refusal("inline of a variable needs line, a line on which it is declared or used.");
    }
    return InlineVariable.run(
        project, args.required("file"), line, args.required("name"), args.integer("column"));
  }

  private static Outcome inlineFunction(SourceProject project, Args args) throws Exception {
    return InlineFunction.run(project, args.required("file"), args.required("name"));
  }

  private static Outcome changeType(SourceProject project, Args args) throws Exception {
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
    Args args =
        new Args(def.name(), values)
            .renamed(ARG_ALIASES.getOrDefault(def.name(), Map.of()))
            .without(RETIRED_ARGS.getOrDefault(def.name(), Set.of()));
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

  /**
   * Arguments a tool still accepts and ignores, by tool: add_declaration places members by kind, so
   * after and before no longer choose where (the user, 2026-11-07).
   */
  static final Map<String, Set<String>> RETIRED_ARGS =
      Map.of("add_declaration", Set.of("after", "before"));

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
}
