package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * The generators of the {@code build} argument: a list whose entries are typed statements or
 * objects that name what to write (a call, a construction, a loop with its body), written as
 * statements whose types come from bindings. The statements are drafted into a copy of the file,
 * with {@code var} where a type is not known yet, and that copy is parsed to read each type.
 */
final class Build {

  private Build() {}

  /** What a build wrote: its statements, not yet indented, and what it inferred. */
  record Built(String text, List<String> inferred) {

    /** "; built Money price; total (assigned)", or nothing. */
    String said() {
      return inferred.isEmpty() ? "" : "; built " + String.join("; ", inferred);
    }
  }

  private static final String RETURN = "return";

  private static final Pattern NAME = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

  /** What a call entry names: a method, with what it is called on. */
  private static final Pattern CALLED =
      Pattern.compile("(?s)(.*\\.)?\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*");

  private static final String SHAPE =
      "build is a list of entries, each a typed statement (\"return total;\") or an object that"
          + " names what to write: {\"call\": \"pricing.priceOf\", \"args\": [\"order\"], \"into\":"
          + " \"price\"}, {\"new\": \"ArrayList\", \"into\": \"open\", \"type\":"
          + " \"List<Order>\"}, {\"loop\": \"orders\", \"body\": [...]} or {\"guard\":"
          + " \"quantity <= 0\", \"exit\": \"return\", \"value\": \"0\"}.";

  /** A loop variable's name in the first draft, before the walked expression's type is known. */
  private static final String ELEMENT = "element$";

  private static final String EACH = "for_each";
  private static final String INDEX = "index";

  private static final String THROW = "throw";

  private static final String REQUIRE = "require";

  /** One entry of a build: a typed statement, or a call, construction or loop to write. */
  private static final class Entry {
    // A typed statement, or null for a generator.
    String typed;
    // call, new or loop, and the method, class or walked expression it names.
    String kind;
    String target;
    List<String> args = List.of();
    // The name the result goes into (or return), and the type to declare it with, when given.
    String into;
    String type;
    // Whether into is a new local, and the type it is written with (null: var, in a draft).
    boolean declares;
    String declared;
    // Whether a construction is written with a diamond.
    boolean diamond;
    // A loop's variable (null until chosen, unless given), form, and the entries of its body.
    String name;
    boolean named;
    // Whether a call or construction's into and diamond are decided, from an earlier draft.
    boolean decided;
    String form = EACH;
    List<Entry> body = List.of();
    // What a loop walks as written (orders, byCustomer.entrySet()) or counts to (names.length);
    // null in the first draft.
    String walked;
    // The index of its first line among the build's lines.
    int line;
    // A guard's exit (return, continue, break, throw or require), what it returns or throws, and
    // the message of a throw or require.
    String exit;
    String value;
    String message;

    boolean loop() {
      return typed == null && kind.equals("loop");
    }

    boolean guard() {
      return typed == null && (kind.equals("guard") || kind.equals("guard_null"));
    }

    /**
     * What a guard leaves on: its condition as typed, or what guard_null names compared to null.
     */
    String condition() {
      return kind.equals("guard") ? target : operand(target) + " == null";
    }

    /** A guard's statement, its exit one {@code step} in: if (cond) { continue; }. */
    String guarded(String step) {
      if (exit.equals(REQUIRE)) {
        return "Objects.requireNonNull(" + target + (message == null ? "" : ", " + message) + ");";
      }
      String leave =
          switch (exit) {
            case RETURN -> value == null ? "return;" : "return " + value + ";";
            case THROW -> "throw new " + value + "(" + (message == null ? "" : message) + ");";
            default -> exit + ";";
          };
      return "if (" + condition() + ") {\n" + step + leave + "\n}";
    }

    /** A loop's head: for (Order order : orders). */
    String head() {
      if (walked == null) {
        return "for (var " + (named ? name : ELEMENT) + " : " + target + ")";
      }
      return form.equals(INDEX)
          ? "for (int " + name + " = 0; " + name + " < " + walked + "; " + name + "++)"
          : "for (" + (declared == null ? "var" : declared) + " " + name + " : " + walked + ")";
    }

    boolean assigns() {
      return typed == null && !loop() && into != null && !into.equals(RETURN);
    }

    String expression() {
      String list = String.join(", ", args);
      return kind.equals("new")
          ? "new " + target + (diamond ? "<>" : "") + "(" + list + ")"
          : target + "(" + list + ")";
    }

    String statement() {
      if (typed != null) {
        return typed;
      }
      if (into == null) {
        return expression() + ";";
      }
      if (into.equals(RETURN)) {
        return "return " + expression() + ";";
      }
      if (!declares) {
        return into + " = " + expression() + ";";
      }
      return (declared == null ? "var" : declared) + " " + into + " = " + expression() + ";";
    }
  }

  /** The file with a build's statements in it, parsed, and the line the first of them is on. */
  private record Draft(String text, CompilationUnit unit, int firstLine, int shift) {

    /** The statement written for {@code e}. */
    Statement at(Entry e) {
      for (Statement s : Nodes.all(unit, Statement.class)) {
        if ((s.getParent() instanceof Block || s.getParent() instanceof SwitchStatement)
            && Lines.number(text, s.getStartPosition()) == firstLine + e.line) {
          return s;
        }
      }
      throw new Refusal(
          "build's entry for "
              + e.target
              + " is not one statement as written: "
              + (e.loop() ? e.head() : e.statement()));
    }

    /** "declared at line 13" of the file as it was, for a declaration a build's loop can see. */
    String where(ASTNode declaration) {
      int line = Lines.number(text, declaration.getStartPosition());
      return line < firstLine
          ? "declared at line " + (line - shift)
          : "declared by an earlier entry of this build";
    }

    /** The call or construction written for {@code e}. */
    Expression expression(Entry e) {
      Statement s = at(e);
      Expression x = null;
      if (s instanceof VariableDeclarationStatement v && v.fragments().size() == 1) {
        x = ((VariableDeclarationFragment) v.fragments().get(0)).getInitializer();
      } else if (s instanceof ReturnStatement r) {
        x = r.getExpression();
      } else if (s instanceof ExpressionStatement es) {
        x =
            es.getExpression() instanceof Assignment a && e.assigns()
                ? a.getRightHandSide()
                : es.getExpression();
      }
      if (e.kind.equals("new")
          ? x instanceof ClassInstanceCreation
          : x instanceof MethodInvocation) {
        return x;
      }
      throw new Refusal(
          "build's entry for "
              + e.target
              + " is not a "
              + (e.kind.equals("new") ? "construction" : "call")
              + " as written: "
              + e.statement());
    }
  }

  /**
   * The statements {@code build} asks for, as they go in place of {@code start} to {@code end} of
   * the opened file (the same offset, to insert them), at {@code indent}, a body one {@code step}
   * further in.
   */
  static Built statements(
      SourceProject project,
      Opened opened,
      int start,
      int end,
      Object build,
      String indent,
      String step,
      List<String> imports)
      throws IOException {
    List<Entry> entries = entries(build, false);
    Site site = new Site(project, opened, start, end, indent, step, imports);
    // First every into as an assignment, to see which names are variables there already, and
    // every loop over what it names, to see what that is.
    // A loop's body is decided in the draft after the loop's own head, since what it walks may
    // name the loop's variable; then each new local and loop variable is written as var, to read
    // its type.
    Draft draft = site.draft(entries);
    while (scope(entries, draft, new HashSet<>())) {
      draft = site.draft(entries);
    }
    List<String> inferred = new ArrayList<>();
    types(entries, draft, inferred);
    return new Built(render(entries, step), inferred);
  }

  /** Where a build's statements go, to draft them there. */
  private record Site(
      SourceProject project,
      Opened opened,
      int start,
      int end,
      String indent,
      String step,
      List<String> imports) {

    Draft draft(List<Entry> entries) throws IOException {
      String before = opened.text();
      String text =
          before.substring(0, start)
              + Primitives.indent(render(entries, step), indent)
              + "\n"
              + before.substring(Math.min(end, before.length()));
      Edits.parses(text, "build, written as statements,");
      Imports.Fixed fixed =
          project.packageOf(opened.file()) == null
              ? new Imports.Fixed(text, 0, List.of())
              : Imports.fix(project, opened.file(), before, text, imports);
      return new Draft(
          fixed.text(),
          Jdt.parse(project, opened.file(), fixed.text()),
          Lines.number(text, start) + fixed.lineShift(),
          fixed.lineShift());
    }
  }

  /**
   * Decides, from a draft, what each entry not yet decided writes: whether an into declares or
   * assigns, a construction's diamond, a loop's form and variable, and a loop's body once its head
   * is decided. {@code taken} are the names the build has given to variables that code at these
   * entries can see. Returns whether anything was decided that the next draft must show.
   */
  private static boolean scope(List<Entry> entries, Draft draft, Set<String> taken) {
    boolean again = false;
    for (Entry e : entries) {
      if (e.typed != null || e.guard()) {
        continue;
      }
      if (e.loop()) {
        if (e.walked == null) {
          walk(e, draft, taken);
          again = true;
          continue;
        }
        Set<String> inner = new HashSet<>(taken);
        inner.add(e.name);
        again |= scope(e.body, draft, inner);
        continue;
      }
      if (e.decided) {
        if (e.declares) {
          taken.add(e.into);
        }
        continue;
      }
      e.decided = true;
      Expression x = draft.expression(e);
      if (x instanceof ClassInstanceCreation c && !e.target.contains("<") && generic(c)) {
        e.diamond = true;
        again = true;
      }
      if (!e.assigns()) {
        continue;
      }
      IBinding b =
          ((Assignment) x.getParent()).getLeftHandSide() instanceof SimpleName n
              ? n.resolveBinding()
              : null;
      if (b instanceof IVariableBinding && !b.isRecovered() || taken.contains(e.into)) {
        if (e.type != null) {
          throw new Refusal(
              e.into
                  + " is a variable there already, so into assigns it and type has nothing to"
                  + " declare. Leave type out, or pass a name that is free.");
        }
        continue;
      }
      taken.add(e.into);
      e.declares = true;
      e.declared = e.type;
      again = true;
    }
    return again;
  }

  /** Checks each call and construction of the second draft and reads each type from it. */
  private static void types(List<Entry> entries, Draft draft, List<String> inferred) {
    for (Entry e : entries) {
      if (e.typed != null) {
        continue;
      }
      if (e.guard()) {
        checkGuard(e, draft);
        continue;
      }
      if (e.loop()) {
        if (e.form.equals(EACH) && e.declared == null) {
          Expression walked = ((EnhancedForStatement) draft.at(e)).getExpression();
          e.declared = Extract.typeName(element(walked.resolveTypeBinding()), around(walked));
        }
        inferred.add(e.head());
        types(e.body, draft, inferred);
        continue;
      }
      Expression x = draft.expression(e);
      check(e, x);
      if (e.declares && e.declared == null) {
        e.declared = typeOf(e, x);
      }
      if (e.assigns()) {
        inferred.add(e.declares ? e.declared + " " + e.into : e.into + " (assigned)");
      }
    }
  }

  /** The entries' statements, one after another, a body one step in; each entry learns its line. */
  private static String render(List<Entry> entries, String step) {
    List<String> lines = new ArrayList<>();
    render(entries, "", step, lines);
    return String.join("\n", lines);
  }

  private static void render(List<Entry> entries, String indent, String step, List<String> lines) {
    for (Entry e : entries) {
      e.line = lines.size();
      if (e.loop()) {
        lines.add(indent + e.head() + " {");
        render(e.body, indent + step, step, lines);
        lines.add(indent + "}");
        continue;
      }
      for (String line : (e.guard() ? e.guarded(step) : e.statement()).split("\n", -1)) {
        lines.add(line.isBlank() ? "" : indent + line);
      }
    }
  }

  /**
   * Decides what a loop entry writes, from the first draft: what it walks (an array, an Iterable, a
   * Map's entries) or counts to, and its variable's name.
   */
  private static void walk(Entry e, Draft draft, Set<String> taken) {
    if (!(draft.at(e) instanceof EnhancedForStatement loop)) {
      throw new Refusal(
          "loop is the expression to walk (orders, byCustomer); got " + e.target + ".");
    }
    Expression of = loop.getExpression();
    ITypeBinding t = of.resolveTypeBinding();
    String operand =
        of instanceof Name
                || of instanceof FieldAccess
                || of instanceof MethodInvocation
                || of instanceof ArrayAccess
                || of instanceof ThisExpression
                || of instanceof ParenthesizedExpression
            ? e.target
            : "(" + e.target + ")";
    boolean index = e.form.equals(INDEX);
    boolean map = false;
    if (t == null || t.isRecovered()) {
      if (e.type == null || index) {
        throw new Refusal(
            e.target
                + " does not resolve there: declare it in an earlier step"
                + (index
                    ? "."
                    : ", or pass type (its elements' type) and it is declared as a stub."));
      }
      e.walked = e.target;
    } else if (t.isArray()) {
      e.walked = index ? operand + ".length" : e.target;
    } else if (LoopToStream.elementOf(t) != null || Hierarchy.isA(t, "java.lang.Iterable")) {
      if (index && !Hierarchy.isA(t, "java.util.List")) {
        throw new Refusal(
            e.target + " is a " + Extract.typeName(t) + ", which has no index: use for_each.");
      }
      if (!index && e.type == null && LoopToStream.elementOf(t) == null) {
        throw new Refusal(
            e.target
                + " is a "
                + Extract.typeName(t)
                + (t.isRawType() ? " with no type argument" : "")
                + ", so its elements' type is not known. Pass type (the elements' type).");
      }
      e.walked = index ? operand + ".size()" : e.target;
    } else if (Hierarchy.isA(t, "java.util.Map")) {
      if (index) {
        throw new Refusal(
            e.target + " is a " + Extract.typeName(t) + ", which has no index: use for_each.");
      }
      map = true;
      e.walked = operand + ".entrySet()";
    } else if (index && List.of("int", "short", "byte", "char").contains(t.getName())) {
      e.walked = e.target;
    } else {
      throw new Refusal(
          e.target
              + " is "
              + article(Extract.typeName(t))
              + ", which a loop cannot walk: loop takes an array, an Iterable, a Map, or (with"
              + " form index) an int.");
    }
    e.declared = index ? null : e.type;
    String chosen = index ? "i" : map ? "entry" : Names.elementName(of);
    for (int n = 2; !free(chosen, loop, taken) || declares(loop.getBody(), chosen); n++) {
      chosen =
          index && n <= 18
              ? String.valueOf((char) ('i' + n - 1))
              : (index ? "i" : chosen.replaceAll("\\d+$", "")) + n;
    }
    if (!e.named) {
      e.name = chosen;
      return;
    }
    ASTNode local = local(e.name, loop);
    if (local != null || taken.contains(e.name)) {
      throw new Refusal(
          e.name
              + " is already a local here ("
              + (local == null ? "declared by an earlier entry of this build" : draft.where(local))
              + "), so the loop's variable cannot have that name. Pass another name, or leave name"
              + " out and it is "
              + chosen
              + ".");
    }
    ITypeBinding owner = fieldOwner(e.name, loop);
    if (owner != null) {
      throw new Refusal(
          e.name
              + " is a field of "
              + Names.typeLabel(owner)
              + ", which the loop's variable would hide in its body. Pass another name, or leave"
              + " name out and it is "
              + chosen
              + ".");
    }
  }

  private static String article(String type) {
    return ("AEIOUaeiou".indexOf(type.charAt(0)) >= 0 ? "an " : "a ") + type;
  }

  /** The type of the elements a for-each over {@code t} gives. */
  private static ITypeBinding element(ITypeBinding t) {
    return t == null ? null : t.isArray() ? t.getComponentType() : LoopToStream.elementOf(t);
  }

  /** Whether a new variable at {@code at} can be named {@code name}. */
  private static boolean free(String name, ASTNode at, Set<String> taken) {
    return !taken.contains(name) && local(name, at) == null && fieldOwner(name, at) == null;
  }

  /** The local or parameter named {@code name} that code at {@code at} can see, or null. */
  private static ASTNode local(String name, ASTNode at) {
    ASTNode child = at;
    for (ASTNode p = at.getParent(); p != null; child = p, p = p.getParent()) {
      List<?> before = List.of();
      if (p instanceof Block b) {
        before = b.statements().subList(0, b.statements().indexOf(child));
      } else if (p instanceof SwitchStatement sw && sw.statements().contains(child)) {
        before = sw.statements().subList(0, sw.statements().indexOf(child));
      } else if (p instanceof ForStatement f && !f.initializers().contains(child)) {
        before = f.initializers();
      } else if (p instanceof EnhancedForStatement f && f.getBody() == child) {
        before = List.of(f.getParameter());
      } else if (p instanceof MethodDeclaration m) {
        before = m.parameters();
      } else if (p instanceof LambdaExpression l) {
        before = l.parameters();
      } else if (p instanceof CatchClause c) {
        before = List.of(c.getException());
      }
      for (Object o : before) {
        List<?> declared =
            o instanceof VariableDeclarationStatement v
                ? v.fragments()
                : o instanceof VariableDeclarationExpression v ? v.fragments() : List.of(o);
        for (Object d : declared) {
          if (d instanceof VariableDeclaration v && v.getName().getIdentifier().equals(name)) {
            return v;
          }
        }
      }
    }
    return null;
  }

  /**
   * The type around {@code at}, or a supertype or enclosing type of it, with a field {@code name}.
   */
  private static ITypeBinding fieldOwner(String name, ASTNode at) {
    for (ITypeBinding t = around(at); t != null; t = t.getDeclaringClass()) {
      for (ITypeBinding s = t; s != null; s = s.getSuperclass()) {
        for (IVariableBinding f : s.getDeclaredFields()) {
          if (f.getName().equals(name)) {
            return s;
          }
        }
      }
    }
    return null;
  }

  /** Refuses a call or construction that cannot be what the entry means. */
  private static void check(Entry e, Expression x) {
    int given = e.args.size();
    if (x instanceof ClassInstanceCreation c) {
      ITypeBinding t = c.getType().resolveBinding();
      if (t == null || t.isRecovered()) {
        return;
      }
      List<IMethodBinding> constructors = new ArrayList<>();
      for (IMethodBinding m : t.getErasure().getDeclaredMethods()) {
        if (m.isConstructor() && !m.isDefaultConstructor()) {
          constructors.add(m);
        }
      }
      if (!constructors.isEmpty() && constructors.stream().noneMatch(m -> fits(m, given))) {
        throw arity("new " + Names.typeLabel(t), constructors, given);
      }
      return;
    }
    MethodInvocation call = (MethodInvocation) x;
    ITypeBinding result = call.resolveTypeBinding();
    if (result != null) {
      if (e.assigns() && result.isPrimitive() && result.getName().equals("void")) {
        IMethodBinding m = call.resolveMethodBinding();
        throw new Refusal(
            (m == null ? e.target : Names.methodLabel(m))
                + " returns nothing, so there is nothing to put in "
                + e.into
                + ". Send the call without into.");
      }
      return;
    }
    List<IMethodBinding> named = named(call);
    String label = label(call, e);
    if (!named.isEmpty() && named.stream().noneMatch(m -> fits(m, given))) {
      throw arity(label, named, given);
    }
    if (!e.declares || e.type != null) {
      return;
    }
    if (named.isEmpty()) {
      throw new Refusal(
          label
              + " is not declared, so the type of its result is not known. Pass type (the type of "
              + e.into
              + "): the call is then written and "
              + label
              + " is declared as a stub returning it.");
    }
    throw new Refusal(
        label
            + " takes "
            + parameters(named)
            + ", which args ("
            + String.join(", ", e.args)
            + ") does not fit as written, so the type of its result is not known. Fix args, or"
            + " pass type (the type of "
            + e.into
            + ").");
  }

  /** The type a new local holding {@code x} is declared with. */
  private static String typeOf(Entry e, Expression x) {
    if (x instanceof ClassInstanceCreation && !e.diamond) {
      return e.target;
    }
    if (e.diamond && e.args.isEmpty()) {
      throw new Refusal(
          e.target
              + " is generic and new "
              + e.target
              + "<>() has no arguments to take its type arguments from. Pass type (the type of "
              + e.into
              + ": List<Order>, "
              + e.target
              + "<Order>).");
    }
    IVariableBinding v =
        x.getParent() instanceof VariableDeclarationFragment f ? f.resolveBinding() : null;
    ITypeBinding t = v == null ? x.resolveTypeBinding() : v.getType();
    if (t == null
        || t.isRecovered()
        || t.isNullType()
        || t.isAnonymous()
        || t.isIntersectionType()) {
      throw new Refusal(
          "the type of "
              + e.expression()
              + " is not one that can be written for "
              + e.into
              + ". Pass type (the type to declare "
              + e.into
              + " with).");
    }
    return Extract.typeName(t, around(x));
  }

  /** Whether {@code c} constructs a generic class without naming its type arguments. */
  private static boolean generic(ClassInstanceCreation c) {
    ITypeBinding t = c.getType().resolveBinding();
    return t != null && (t.isRawType() || t.isGenericType());
  }

  private static boolean fits(IMethodBinding m, int given) {
    int takes = m.getParameterTypes().length;
    return m.isVarargs() ? given >= takes - 1 : given == takes;
  }

  private static Refusal arity(String label, List<IMethodBinding> candidates, int given) {
    boolean one = candidates.size() == 1;
    return new Refusal(
        label
            + " takes "
            + parameters(candidates)
            + "; args has "
            + given
            + (one ? " of " + candidates.get(0).getParameterTypes().length : "")
            + (one ? ". Pass each, in that order." : ". Pass one of those lists, in its order."));
  }

  /** "(Order order, String customer)", one list per method, joined by "or". */
  private static String parameters(List<IMethodBinding> methods) {
    Set<String> lists = new LinkedHashSet<>();
    for (IMethodBinding m : methods) {
      String[] names = m.getMethodDeclaration().getParameterNames();
      ITypeBinding[] types = m.getMethodDeclaration().getParameterTypes();
      List<String> parameters = new ArrayList<>();
      for (int i = 0; i < types.length; i++) {
        parameters.add(Extract.typeName(types[i]) + (i < names.length ? " " + names[i] : ""));
      }
      lists.add("(" + String.join(", ", parameters) + ")");
    }
    return String.join(" or ", lists);
  }

  /** The methods a call's name could mean: those of that name in what it is called on. */
  private static List<IMethodBinding> named(MethodInvocation call) {
    List<IMethodBinding> out = new ArrayList<>();
    if (call.getExpression() != null) {
      named(call.getExpression().resolveTypeBinding(), call.getName().getIdentifier(), out);
      return out;
    }
    for (ITypeBinding t = around(call); t != null; t = t.getDeclaringClass()) {
      named(t, call.getName().getIdentifier(), out);
    }
    return out;
  }

  private static void named(ITypeBinding t, String name, List<IMethodBinding> out) {
    if (t == null || t.isRecovered()) {
      return;
    }
    for (ITypeBinding s : Hierarchy.supertypes(t, true)) {
      if (s.isRecovered()) {
        continue;
      }
      for (IMethodBinding m : s.getErasure().getDeclaredMethods()) {
        if (m.getName().equals(name) && !m.isConstructor()) {
          out.add(m);
        }
      }
    }
  }

  /** "Pricing.priceOf": the call's method with the type it is called on, when that is known. */
  private static String label(MethodInvocation call, Entry e) {
    ITypeBinding t =
        call.getExpression() == null ? around(call) : call.getExpression().resolveTypeBinding();
    return t == null || t.isRecovered()
        ? e.target
        : Names.typeLabel(t) + "." + call.getName().getIdentifier();
  }

  /** The type whose code {@code node} is in. */
  private static ITypeBinding around(ASTNode node) {
    for (ASTNode p = node.getParent(); p != null; p = p.getParent()) {
      if (p instanceof AbstractTypeDeclaration t) {
        return t.resolveBinding();
      }
      if (p instanceof AnonymousClassDeclaration a) {
        return a.resolveBinding();
      }
    }
    return null;
  }

  private static List<Entry> entries(Object build, boolean body) {
    if (!(build instanceof List<?> items) || items.isEmpty() && !body) {
      throw new Refusal(SHAPE);
    }
    List<Entry> out = new ArrayList<>();
    for (Object item : items) {
      Entry e = new Entry();
      if (item instanceof String s && !s.isBlank()) {
        e.typed = Primitives.indent(s, "");
      } else if (item instanceof Map<?, ?> map) {
        generator(map, e);
      } else {
        throw new Refusal(SHAPE + " Got " + item + ".");
      }
      out.add(e);
    }
    return out;
  }

  private static void generator(Map<?, ?> map, Entry e) {
    List<String> kinds = new ArrayList<>();
    for (String kind : List.of("call", "new", "loop", "guard", "guard_null")) {
      if (map.containsKey(kind)) {
        kinds.add(kind);
      }
    }
    if (kinds.size() != 1) {
      throw new Refusal(
          "a build entry names one thing to write, by the key call, new, loop, guard or"
              + " guard_null; this one has "
              + (map.isEmpty() ? "no keys" : String.join(", ", keys(map)))
              + ".");
    }
    e.kind = kinds.get(0);
    List<String> takes =
        e.kind.equals("loop")
            ? List.of("loop", "name", "form", "type", "body")
            : e.kind.startsWith("guard")
                ? List.of(e.kind, "exit", "value", "message")
                : List.of(e.kind, "args", "into", "type");
    for (String key : keys(map)) {
      if (!takes.contains(key)) {
        throw new Refusal(
            "a "
                + e.kind
                + " entry takes "
                + String.join(", ", takes.subList(0, takes.size() - 1))
                + " and "
                + takes.get(takes.size() - 1)
                + "; "
                + key
                + " is none of these.");
      }
    }
    e.target = string(map, e.kind, e).strip();
    if (e.kind.equals("call") && !CALLED.matcher(e.target).matches()) {
      throw new Refusal(
          "call is the method's name, with what it is called on (priceOf, pricing.priceOf,"
              + " Money.zero), without its arguments: those go in args. Got "
              + e.target
              + ".");
    }
    if (e.kind.equals("loop")) {
      loop(map, e);
      return;
    }
    if (e.kind.startsWith("guard")) {
      exits(map, e);
      return;
    }
    Object args = map.get("args");
    if (args != null) {
      if (!(args instanceof List<?> list)
          || !list.stream().allMatch(a -> a instanceof String s && !s.isBlank())) {
        throw new Refusal(
            "args is the list of the "
                + e.kind
                + "'s arguments, each an expression; got "
                + args
                + ".");
      }
      e.args = list.stream().map(a -> ((String) a).strip()).toList();
    }
    if (map.containsKey("into")) {
      e.into = string(map, "into", e).strip();
      if (!NAME.matcher(e.into).matches()) {
        throw new Refusal(
            "into is the name of the variable the result goes into (a new local, or one that is"
                + " there), or return; got "
                + e.into
                + ".");
      }
    }
    if (map.containsKey("type")) {
      e.type = string(map, "type", e).strip();
      if (e.into == null || e.into.equals(RETURN)) {
        throw new Refusal(
            "type is the type a new local is declared with, so it goes with into (its name).");
      }
    }
  }

  private static void loop(Map<?, ?> map, Entry e) {
    if (map.containsKey("name")) {
      e.name = string(map, "name", e).strip();
      e.named = true;
      if (!NAME.matcher(e.name).matches()) {
        throw new Refusal("name is the loop variable's name; got " + e.name + ".");
      }
    }
    if (map.containsKey("form")) {
      e.form = string(map, "form", e).strip();
      if (!e.form.equals(EACH) && !e.form.equals(INDEX)) {
        throw new Refusal(
            "form is for_each (the default) or index; got "
                + e.form
                + ". For another form, build the for_each and send convert_loop on its line.");
      }
    }
    if (map.containsKey("type")) {
      e.type = string(map, "type", e).strip();
      if (e.form.equals(INDEX)) {
        throw new Refusal("type is the elements' type of a for_each; an index loop has none.");
      }
    }
    if (map.containsKey("body")) {
      e.body = entries(map.get("body"), true);
    }
  }

  private static List<String> keys(Map<?, ?> map) {
    return map.keySet().stream().map(String::valueOf).toList();
  }

  private static String string(Map<?, ?> map, String key, Entry e) {
    if (map.get(key) instanceof String s && !s.isBlank()) {
      return s;
    }
    throw new Refusal(
        key
            + " of a "
            + e.kind
            + " entry is a string that is not empty; got "
            + map.get(key)
            + ".");
  }

  /**
   * A loop around the statements from {@code start} to {@code end} of the opened file: surround's
   * loop object, whose body is those statements.
   */
  static Built around(
      SourceProject project,
      Opened opened,
      int start,
      int end,
      Map<?, ?> loop,
      String indent,
      String step,
      List<String> imports)
      throws IOException {
    if (!loop.containsKey("loop")
        || !List.of("loop", "name", "form", "type").containsAll(keys(loop))) {
      throw new Refusal(
          "surround's loop takes loop, name, form and type: its body is the statements from"
              + " start_line to end_line. To write other statements, send build.");
    }
    Map<Object, Object> entry = new LinkedHashMap<>(loop);
    entry.put("body", List.of(opened.text().substring(start, end).stripTrailing()));
    return statements(project, opened, start, end, List.of(entry), indent, step, imports);
  }

  /** Whether code in {@code body} declares a variable named {@code name}. */
  private static boolean declares(ASTNode body, String name) {
    return Nodes.all(body, VariableDeclaration.class).stream()
        .anyMatch(v -> v.getName().getIdentifier().equals(name));
  }

  /** Reads a guard's exit, value and message, refusing what the exit does not take. */
  private static void exits(Map<?, ?> map, Entry e) {
    List<String> exits = new ArrayList<>(List.of(RETURN, "continue", "break", THROW));
    if (e.kind.equals("guard_null")) {
      exits.add(REQUIRE);
    }
    String choices =
        String.join(", ", exits.subList(0, exits.size() - 1))
            + " or "
            + exits.get(exits.size() - 1);
    if (!map.containsKey("exit")) {
      throw new Refusal("a " + e.kind + " entry needs exit, how it leaves: " + choices + ".");
    }
    e.exit = string(map, "exit", e).strip();
    if (!exits.contains(e.exit)) {
      throw new Refusal("exit of a " + e.kind + " is " + choices + "; got " + e.exit + ".");
    }
    if (map.containsKey("value")) {
      e.value = string(map, "value", e).strip();
      if (!e.exit.equals(RETURN) && !e.exit.equals(THROW)) {
        throw new Refusal(
            "value is what return returns or what throw throws; " + e.exit + " takes none.");
      }
    } else if (e.exit.equals(THROW)) {
      throw new Refusal(
          "throw needs value, the type of the exception to throw (IllegalArgumentException).");
    }
    if (map.containsKey("message")) {
      e.message = string(map, "message", e).strip();
      if (!e.exit.equals(THROW) && !e.exit.equals(REQUIRE)) {
        throw new Refusal(
            "message is the exception's message, for throw or require; " + e.exit + " takes none.");
      }
    }
  }

  /**
   * Refuses a guard that cannot mean what it says where it is: a condition that is not boolean, a
   * primitive compared to null, a return without the value its method needs or with one it cannot
   * take, a continue or break with nothing to leave, a checked exception nothing declares.
   */
  private static void checkGuard(Entry e, Draft draft) {
    Statement s = draft.at(e);
    Expression tested = null;
    if (s instanceof IfStatement i) {
      tested = i.getExpression();
      if (e.kind.equals("guard_null") && tested instanceof InfixExpression n) {
        tested = n.getLeftOperand();
      }
    } else if (s instanceof ExpressionStatement x
        && x.getExpression() instanceof MethodInvocation m
        && !m.arguments().isEmpty()) {
      tested = (Expression) m.arguments().get(0);
    }
    if (tested == null) {
      throw new Refusal(
          "build's entry for " + e.target + " is not one statement as written: " + e.guarded("  "));
    }
    ITypeBinding t = tested.resolveTypeBinding();
    if (t != null && !t.isRecovered()) {
      if (e.kind.equals("guard")
          && !t.getName().equals("boolean")
          && !t.getQualifiedName().equals("java.lang.Boolean")) {
        throw new Refusal(e.target + " is " + article(Extract.typeName(t)) + ", not a condition.");
      }
      if (e.kind.equals("guard_null") && t.isPrimitive()) {
        throw new Refusal(e.target + " is " + article(t.getName()) + ", which is never null.");
      }
    }
    ASTNode owner = owner(s);
    IMethodBinding m =
        owner instanceof MethodDeclaration d
            ? d.resolveBinding()
            : owner instanceof LambdaExpression l ? l.resolveMethodBinding() : null;
    String label =
        m == null
            ? "the code there"
            : owner instanceof LambdaExpression ? "the lambda" : Names.methodLabel(m);
    String when = e.kind.equals("guard") ? e.target : e.target + " is null";
    if (e.exit.equals(RETURN) && m != null) {
      ITypeBinding r = m.getReturnType();
      boolean none = m.isConstructor() || r.getName().equals("void");
      if (none && e.value != null) {
        throw new Refusal(label + " returns nothing, so return takes no value. Leave value out.");
      }
      if (!none && e.value == null) {
        throw new Refusal(
            label
                + " returns "
                + Extract.typeName(r, around(s))
                + ", so return needs a value. Pass value: what to return when "
                + when
                + ".");
      }
    }
    if (e.exit.equals("continue") || e.exit.equals("break")) {
      boolean out = e.exit.equals("break");
      boolean inSwitch = false;
      for (ASTNode p = s.getParent(); p != null && p != owner; p = p.getParent()) {
        inSwitch |= p instanceof SwitchStatement;
      }
      if (Effects.loopOf(s, owner) == null && !(out && inSwitch)) {
        throw new Refusal(
            "The guard is not inside a loop"
                + (out ? " or a switch" : "")
                + " there, so there is nothing to "
                + e.exit
                + ". The exits there are return and throw.");
      }
    }
    if (e.exit.equals(THROW)) {
      ThrowStatement thrown = Nodes.all(s, ThrowStatement.class).get(0);
      ITypeBinding x = thrown.getExpression().resolveTypeBinding();
      if (x == null
          || x.isRecovered()
          || !Exceptions.checked(x)
          || Exceptions.caught(thrown, owner, x)) {
        return;
      }
      if (m != null && List.of(m.getExceptionTypes()).stream().anyMatch(x::isSubTypeCompatible)) {
        return;
      }
      String name = Extract.typeName(x, around(s));
      throw new Refusal(
          label
              + " does not declare "
              + name
              + ". Send change_signature with add_throws "
              + name
              + " before this step in the same batch (its callers follow), or throw an unchecked"
              + " exception.");
    }
  }

  /** The method, initializer or lambda whose body {@code s} is in. */
  private static ASTNode owner(ASTNode s) {
    ASTNode p = s.getParent();
    while (p != null && !(p instanceof BodyDeclaration) && !(p instanceof LambdaExpression)) {
      p = p.getParent();
    }
    return p;
  }

  /**
   * {@code x} as the operand of a comparison: in parentheses unless it is a name, call or access.
   */
  private static String operand(String x) {
    String outside = x;
    for (String inner = ""; !inner.equals(outside); ) {
      inner = outside;
      outside = outside.replaceAll("\\([^()]*\\)", "_");
    }
    return outside.matches("[\\p{L}\\p{N}_$.\\[\\]]+") ? x : "(" + x + ")";
  }

  /**
   * The call or construction {@code build} names (one entry), as it goes in place of {@code start}
   * to {@code end} of the opened file: with a diamond where its class is generic, checked as a call
   * entry is.
   */
  static String expression(
      SourceProject project, Opened opened, int start, int end, Object build, List<String> imports)
      throws IOException {
    String shape =
        "replace_expression's build is one call or construction: {\"call\": \"discounts.apply\","
            + " \"args\": [\"price\"]} or {\"new\": \"Receipt\", \"args\": [\"order\"]}.";
    if (!(build instanceof Map<?, ?> map)) {
      throw new Refusal(shape + " Got " + build + ".");
    }
    Entry e = new Entry();
    generator(map, e);
    if (!e.kind.equals("call") && !e.kind.equals("new")) {
      throw new Refusal(
          shape
              + " A "
              + e.kind
              + " is a statement: send it as build of insert_statements or replace_statements.");
    }
    if (e.into != null) {
      throw new Refusal(
          "replace_expression's build is the expression alone, so it takes no into: the code"
              + " around it uses the value.");
    }
    Expression x = drafted(project, opened, start, end, e, imports);
    if (x instanceof ClassInstanceCreation c && !e.target.contains("<") && generic(c)) {
      e.diamond = true;
      x = drafted(project, opened, start, end, e, imports);
    }
    check(e, x);
    return e.expression();
  }

  /** The call or construction {@code e} writes, parsed in place of {@code start} to {@code end}. */
  private static Expression drafted(
      SourceProject project, Opened opened, int start, int end, Entry e, List<String> imports)
      throws IOException {
    String before = opened.text();
    String written = e.expression();
    String text = before.substring(0, start) + written + before.substring(end);
    Edits.parses(text, "build, written as an expression,");
    Imports.Fixed fixed =
        project.packageOf(opened.file()) == null
            ? new Imports.Fixed(text, 0, List.of())
            : Imports.fix(project, opened.file(), before, text, imports);
    // The import fix changes only lines above the expression.
    int at = start + fixed.text().length() - text.length();
    CompilationUnit unit = Jdt.parse(project, opened.file(), fixed.text());
    for (Expression x : Nodes.all(unit, Expression.class)) {
      if (x.getStartPosition() == at
          && x.getLength() == written.length()
          && (x instanceof MethodInvocation || x instanceof ClassInstanceCreation)) {
        return x;
      }
    }
    throw new Refusal(
        "build's entry for " + e.target + " is not one expression as written there: " + written);
  }
}
