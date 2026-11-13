package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * The generators of the {@code build} argument: a list whose entries are typed statements or
 * objects that name what to write (a call, a construction), written as statements whose types come
 * from bindings. The statements are drafted into a copy of the file, with {@code var} where a type
 * is not known yet, and that copy is parsed to read each type.
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
          + " \"price\"} or {\"new\": \"ArrayList\", \"into\": \"open\", \"type\":"
          + " \"List<Order>\"}.";

  /** One entry of a build: a typed statement, or a call or construction to write. */
  private static final class Entry {
    // A typed statement, or null for a generator.
    String typed;
    // call or new, and the method or class it names.
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
    // The index of its first line among the build's lines.
    int line;

    boolean assigns() {
      return typed == null && into != null && !into.equals(RETURN);
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
  private record Draft(String text, CompilationUnit unit, int firstLine) {

    /** The statement written for {@code e}. */
    Statement at(Entry e) {
      for (Statement s : Nodes.all(unit, Statement.class)) {
        if ((s.getParent() instanceof Block || s.getParent() instanceof SwitchStatement)
            && Lines.number(text, s.getStartPosition()) == firstLine + e.line) {
          return s;
        }
      }
      throw new Refusal(
          "build's entry for " + e.target + " is not one statement as written: " + e.statement());
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
   * the opened file (the same offset, to insert them), at {@code indent}.
   */
  static Built statements(
      SourceProject project,
      Opened opened,
      int start,
      int end,
      Object build,
      String indent,
      List<String> imports)
      throws IOException {
    List<Entry> entries = entries(build);
    // First every into as an assignment, to see which names are variables there already.
    Draft draft = draft(project, opened, start, end, entries, indent, imports);
    Set<String> declared = new HashSet<>();
    boolean again = false;
    for (Entry e : entries) {
      if (e.typed != null) {
        continue;
      }
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
      if (b instanceof IVariableBinding && !b.isRecovered() || declared.contains(e.into)) {
        if (e.type != null) {
          throw new Refusal(
              e.into
                  + " is a variable there already, so into assigns it and type has nothing to"
                  + " declare. Leave type out, or pass a name that is free.");
        }
        continue;
      }
      declared.add(e.into);
      e.declares = true;
      e.declared = e.type;
      again = true;
    }
    // Then each new local as var, to read its type.
    if (again) {
      draft = draft(project, opened, start, end, entries, indent, imports);
    }
    List<String> inferred = new ArrayList<>();
    for (Entry e : entries) {
      if (e.typed != null) {
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
    return new Built(render(entries), inferred);
  }

  /** The entries' statements, one after another; each entry learns its line. */
  private static String render(List<Entry> entries) {
    List<String> lines = new ArrayList<>();
    for (Entry e : entries) {
      e.line = lines.size();
      lines.addAll(List.of(e.statement().split("\n", -1)));
    }
    return String.join("\n", lines);
  }

  private static Draft draft(
      SourceProject project,
      Opened opened,
      int start,
      int end,
      List<Entry> entries,
      String indent,
      List<String> imports)
      throws IOException {
    String before = opened.text();
    String text =
        before.substring(0, start)
            + Primitives.indent(render(entries), indent)
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
        Lines.number(text, start) + fixed.lineShift());
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
            (m == null ? e.target : Signatures.label(m))
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
    for (IMethodBinding m : t.getErasure().getDeclaredMethods()) {
      if (m.getName().equals(name) && !m.isConstructor()) {
        out.add(m);
      }
    }
    named(t.getSuperclass(), name, out);
    for (ITypeBinding i : t.getInterfaces()) {
      named(i, name, out);
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

  private static List<Entry> entries(Object build) {
    if (!(build instanceof List<?> items) || items.isEmpty()) {
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
    for (String kind : List.of("call", "new")) {
      if (map.containsKey(kind)) {
        kinds.add(kind);
      }
    }
    if (kinds.size() != 1) {
      throw new Refusal(
          "a build entry names one thing to write, by the key call or new; this one has "
              + (map.isEmpty() ? "no keys" : String.join(", ", keys(map)))
              + ".");
    }
    e.kind = kinds.get(0);
    List<String> takes = List.of(e.kind, "args", "into", "type");
    for (String key : keys(map)) {
      if (!takes.contains(key)) {
        throw new Refusal(
            "a "
                + e.kind
                + " entry takes "
                + e.kind
                + ", args, into and type; "
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
}
