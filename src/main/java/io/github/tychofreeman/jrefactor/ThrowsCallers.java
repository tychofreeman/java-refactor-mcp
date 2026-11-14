package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * What the callers of a method need when it declares a new checked exception (change_signature's
 * {@code callers}): with propagate, each calling method declares it too, and its callers after it,
 * up to a method named by stop_at or one that cannot declare it (an override of a library's method,
 * a lambda of a library's interface, an initializer), where the call is caught and rethrown
 * unchecked; with catch, every call is. No body throws the exception yet, so either keeps behavior.
 */
final class ThrowsCallers {
  /** The declarations that gain a throws by propagation, and which exceptions each gains. */
  final List<Signatures.Decl> decls = new ArrayList<>();

  final Map<MethodDeclaration, List<String>> names = new IdentityHashMap<>();

  /** Where a catch was written, and the imports the catches need. */
  final List<String> caught = new ArrayList<>();

  final List<String> imports = new ArrayList<>();

  /** The stop_at methods a call path did not reach. */
  final Set<String> unreached = new LinkedHashSet<>();

  private record Site(Signatures.Source s, ASTNode node) {}

  /** A catch to write: the exception as written, the unchecked type that wraps it. */
  private record Caught(String name, String wrapper) {}

  /** Statements {@code from} to {@code to} of a block, to go in a try with these catches. */
  private static final class Wrap {
    final Signatures.Source s;
    final Block block;
    int from;
    int to;
    final String var;
    final List<Caught> catches = new ArrayList<>();

    Wrap(Signatures.Source s, Block block, int from, int to, String var) {
      this.s = s;
      this.block = block;
      this.from = from;
      this.to = to;
      this.var = var;
    }

    int start() {
      return ((ASTNode) block.statements().get(from)).getStartPosition();
    }

    int end() {
      return Nodes.end((ASTNode) block.statements().get(to));
    }
  }

  private final SourceProject project;
  private final MethodFamily family;
  private final SignatureChange change;
  private final boolean propagate;
  private final List<String> stops;
  private final Map<String, List<Site>> calls = new HashMap<>();
  private final Map<String, List<Site>> references = new HashMap<>();
  private final Map<String, Signatures.Decl> declared = new HashMap<>();
  private final List<CompilationUnit> units = new ArrayList<>();
  private final Set<MethodDeclaration> first = new HashSet<>();
  private final List<Wrap> wraps = new ArrayList<>();
  private final Map<TryStatement, List<Caught>> clauses = new IdentityHashMap<>();
  private final Map<TryStatement, Signatures.Source> tries = new IdentityHashMap<>();

  private ThrowsCallers(
      SourceProject project,
      MethodFamily family,
      SignatureChange change,
      boolean propagate,
      List<String> stops) {
    this.project = project;
    this.family = family;
    this.change = change;
    this.propagate = propagate;
    this.stops = stops;
    this.unreached.addAll(stops);
  }

  /**
   * Handles the calls of {@code initial}, the declarations that gain {@code added}: the edits of
   * the catches go into {@code change}, and the declarations that must gain a throws are returned
   * in {@link #decls}.
   */
  static ThrowsCallers handle(
      SourceProject project,
      MethodFamily family,
      SignatureChange change,
      List<Signatures.Decl> initial,
      List<String> added,
      boolean propagate,
      List<String> stops,
      List<String> chosen)
      throws Exception {
    ThrowsCallers c = new ThrowsCallers(project, family, change, propagate, stops);
    c.index();
    for (Signatures.Decl d : initial) {
      c.first.add(d.m());
    }
    for (String name : added) {
      Set<String> supers = c.supersOf(name, chosen);
      if (supers.contains("java.lang.RuntimeException") || supers.contains("java.lang.Error")) {
        continue;
      }
      c.spread(initial, name, supers);
    }
    if (!c.unreached.isEmpty()) {
      throw new Refusal(
          "stop_at "
              + String.join(", ", c.unreached)
              + " was not reached: no chain of calls that needs the exception passes through it."
              + " Name a method that calls "
              + family.label
              + ", directly or through others, as Type.method.");
    }
    c.write();
    return c;
  }

  /** Parses every file of the project not yet parsed, and indexes calls and declarations by key. */
  private void index() throws Exception {
    Holding missing = Holding.where(project, (f, t) -> !family.sources.containsKey(f));
    for (Map.Entry<Path, CompilationUnit> e : missing.units().entrySet()) {
      Path f = e.getKey();
      family.sources.put(
          f, new Signatures.Source(f, project.relative(f), missing.texts().get(f), e.getValue()));
    }
    for (Signatures.Source s : family.sources.values()) {
      units.add(s.unit());
      for (ASTNode n : Nodes.all(s.unit(), ASTNode.class)) {
        IMethodBinding b = Calls.binding(n);
        boolean reference = n instanceof MethodReference;
        if (n instanceof MethodDeclaration m && m.resolveBinding() != null) {
          declared.put(
              Signatures.key(m.resolveBinding()),
              new Signatures.Decl(
                  s, m, m.resolveBinding(), Signatures.open(s.text(), m.getName())));
        }
        if (b != null) {
          (reference ? references : calls)
              .computeIfAbsent(Signatures.key(b), k -> new ArrayList<>())
              .add(new Site(s, n));
        }
      }
    }
  }

  /**
   * The qualified names of the exception {@code name} and its superclasses, as it resolves in the
   * target's file with {@code chosen} as imports.
   */
  private Set<String> supersOf(String name, List<String> chosen) throws Exception {
    Signatures.Source home = null;
    for (Signatures.Source s : family.sources.values()) {
      if (s.unit() == family.target.getRoot()) {
        home = s;
      }
    }
    return new HashSet<>(
        exceptionSupers(
            project, home.file(), home.rel(), home.text(), family.target, name, chosen));
  }

  /**
   * The qualified names of the exception {@code name} and its superclasses, nearest first, as it
   * resolves in the type around {@code at} in {@code file} ({@code text}) with {@code chosen} as
   * imports ({@link Jdt#probeType}).
   */
  static List<String> exceptionSupers(
      SourceProject project,
      Path file,
      String rel,
      String text,
      ASTNode at,
      String name,
      List<String> chosen)
      throws Exception {
    ASTNode type = at.getParent();
    while (!(type instanceof AbstractTypeDeclaration)) {
      type = type.getParent();
    }
    List<String> out = new ArrayList<>();
    for (ITypeBinding t =
            Jdt.probeType(project, file, text, (AbstractTypeDeclaration) type, name, chosen);
        t != null;
        t = t.getSuperclass()) {
      if (!t.isRecovered()) {
        out.add(t.getErasure().getQualifiedName());
      }
    }
    if (!out.contains("java.lang.Throwable")) {
      throw new Refusal(
          name
              + " does not resolve to an exception type in "
              + rel
              + ". Pass its qualified name in imports.");
    }
    return out;
  }

  /** Follows the calls of the declarations that throw {@code name}, outwards. */
  private void spread(List<Signatures.Decl> initial, String name, Set<String> supers) {
    Set<String> throwing = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    for (Signatures.Decl d : initial) {
      if (throwing.add(Signatures.key(d.binding()))) {
        queue.add(Signatures.key(d.binding()));
      }
    }
    while (!queue.isEmpty()) {
      String key = queue.pop();
      for (Site site : calls.getOrDefault(key, List.of())) {
        call(site, name, supers, throwing, queue);
      }
      for (Site site : references.getOrDefault(key, List.of())) {
        IMethodBinding f = functional(site.node());
        if (f == null || allows(f, supers, throwing) || declare(f, name, throwing, queue)) {
          continue;
        }
        throw new Refusal(
            where(site)
                + " uses a method that would throw "
                + name
                + " as a method reference, where it can be neither declared nor caught. Turn it"
                + " into a lambda first (convert_lambda).");
      }
    }
  }

  /**
   * One call of a method that throws {@code name}: handled already, declared further, or caught.
   */
  private void call(
      Site site, String name, Set<String> supers, Set<String> throwing, Deque<String> queue) {
    ASTNode n = site.node();
    TryStatement inner = null;
    ASTNode context = null;
    ASTNode child = n;
    for (ASTNode p = n.getParent(); p != null; child = p, p = p.getParent()) {
      if (p instanceof TryStatement t && (child == t.getBody() || t.resources().contains(child))) {
        if (catches(t, supers)) {
          return;
        }
        if (inner == null) {
          inner = t;
        }
      } else if (p instanceof LambdaExpression
          || p instanceof MethodDeclaration
          || p instanceof Initializer) {
        context = p;
        break;
      } else if (p instanceof AbstractTypeDeclaration) {
        break;
      }
    }
    if (context == null) {
      throw new Refusal(
          where(site)
              + " calls it in a field's initializer or an enum constant, where "
              + name
              + " can be neither declared nor caught. Move the call into a constructor or an"
              + " initializer block first.");
    }
    MethodDeclaration method = null;
    for (ASTNode p = n; p != null && method == null; p = p.getParent()) {
      method = p instanceof MethodDeclaration m ? m : null;
    }
    String stop = method == null ? null : stopOf(method);
    IMethodBinding declarer =
        context instanceof LambdaExpression
            ? functional(context)
            : context instanceof MethodDeclaration m ? m.resolveBinding() : null;
    if (declarer != null && allows(declarer, supers, throwing)) {
      return;
    }
    if (stop != null) {
      unreached.remove(stop);
    } else if (propagate && declarer != null && declare(declarer, name, throwing, queue)) {
      return;
    }
    String wrapper = "IllegalStateException";
    if (supers.contains("java.io.IOException")) {
      wrapper = "UncheckedIOException";
      if (!imports.contains("java.io.UncheckedIOException")) {
        imports.add("java.io.UncheckedIOException");
      }
    }
    catchAt(site, context, inner, new Caught(name, wrapper));
  }

  /** The method of the functional interface that the lambda or method reference {@code n} is. */
  private static IMethodBinding functional(ASTNode n) {
    ITypeBinding t = ((Expression) n).resolveTypeBinding();
    return t == null ? null : t.getFunctionalInterfaceMethod();
  }

  /** Whether {@code b} throws the exception already, or will. */
  private static boolean allows(IMethodBinding b, Set<String> supers, Set<String> throwing) {
    if (throwing.contains(Signatures.key(b))) {
      return true;
    }
    for (ITypeBinding t : b.getExceptionTypes()) {
      if (supers.contains(t.getErasure().getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  /** Whether a catch of {@code t} catches the exception. */
  private static boolean catches(TryStatement t, Set<String> supers) {
    for (Object o : t.catchClauses()) {
      Type type = ((CatchClause) o).getException().getType();
      List<?> types = type instanceof UnionType u ? u.types() : List.of(type);
      for (Object x : types) {
        ITypeBinding b = ((Type) x).resolveBinding();
        if (b != null && supers.contains(b.getErasure().getQualifiedName())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Adds {@code name} to the throws of {@code b} and of every declaration of its family, whose
   * calls are then followed too; false when the project does not declare it or it overrides a
   * library's method, which cannot throw more.
   */
  private boolean declare(
      IMethodBinding b, String name, Set<String> throwing, Deque<String> queue) {
    if (!declared.containsKey(Signatures.key(b))) {
      return false;
    }
    Set<String> keys;
    try {
      keys = Signatures.family(project, units, b, "");
    } catch (Refusal library) {
      return false;
    }
    for (String key : keys) {
      Signatures.Decl d = declared.get(key);
      if (d == null) {
        continue;
      }
      if (throwing.add(key)) {
        queue.add(key);
      }
      if (first.contains(d.m())) {
        continue;
      }
      if (!names.containsKey(d.m())) {
        decls.add(d);
      }
      List<String> list = names.computeIfAbsent(d.m(), k -> new ArrayList<>());
      if (!list.contains(name)) {
        list.add(name);
      }
    }
    return true;
  }

  /** The stop_at name that {@code m} is, or null. */
  private String stopOf(MethodDeclaration m) {
    String label = Names.typeLabelAt(m) + "." + m.getName().getIdentifier();
    for (String stop : stops) {
      String s = stop.contains("(") ? stop.substring(0, stop.indexOf('(')) : stop;
      if (label.equals(s) || label.endsWith("." + s)) {
        return stop;
      }
    }
    return null;
  }

  /** Records a catch of {@code c} around the call at {@code site}. */
  private void catchAt(Site site, ASTNode context, TryStatement inner, Caught c) {
    Signatures.Source s = site.s();
    String at = where(site);
    if (!caught.contains(at)) {
      caught.add(at);
    }
    if (inner != null) {
      tries.put(inner, s);
      List<Caught> list = clauses.computeIfAbsent(inner, k -> new ArrayList<>());
      if (!list.contains(c)) {
        list.add(c);
      }
      return;
    }
    ASTNode top = context;
    for (ASTNode p = context; p != null; p = p.getParent()) {
      if (p instanceof MethodDeclaration || p instanceof Initializer) {
        top = p;
      }
    }
    String var = "e";
    Set<String> used = new HashSet<>();
    for (SimpleName name : Nodes.all(top, SimpleName.class)) {
      used.add(name.getIdentifier());
    }
    for (int k = 2; used.contains(var); k++) {
      var = "e" + k;
    }
    if (context instanceof LambdaExpression lambda && !(lambda.getBody() instanceof Block)) {
      String text = s.text();
      String indent = Lines.indentOf(text, lambda.getStartPosition());
      String step = step(text);
      IMethodBinding f = functional(lambda);
      boolean returns = f != null && !f.getReturnType().getName().equals("void");
      ASTNode body = lambda.getBody();
      change.replace(
          s,
          body,
          "{\n"
              + indent
              + step
              + "try {\n"
              + indent
              + step
              + step
              + (returns ? "return " : "")
              + text.substring(body.getStartPosition(), Nodes.end(body))
              + ";\n"
              + indent
              + step
              + "}"
              + clause(c, var, indent + step, step)
              + "\n"
              + indent
              + "}");
      return;
    }
    ASTNode st = site.node();
    while (!(st instanceof Statement && st.getParent() instanceof Block)) {
      st = st.getParent();
    }
    if (st instanceof ConstructorInvocation || st instanceof SuperConstructorInvocation) {
      throw new Refusal(
          at
              + " calls it from this(...) or super(...), which a try cannot hold, and "
              + c.name()
              + " cannot be declared there.");
    }
    Block block = (Block) st.getParent();
    int from = block.statements().indexOf(st);
    int to = from;
    if (st instanceof VariableDeclarationStatement v) {
      Set<String> keys = new HashSet<>();
      for (Object o : v.fragments()) {
        IVariableBinding b = ((VariableDeclarationFragment) o).resolveBinding();
        if (b != null) {
          keys.add(b.getKey());
        }
      }
      for (int k = from + 1; k < block.statements().size(); k++) {
        for (SimpleName name : Nodes.all((ASTNode) block.statements().get(k), SimpleName.class)) {
          if (name.resolveBinding() != null && keys.contains(name.resolveBinding().getKey())) {
            to = block.statements().size() - 1;
          }
        }
      }
    }
    Wrap wrap = new Wrap(s, block, from, to, var);
    wrap.catches.add(c);
    wraps.add(wrap);
  }

  /** Writes the catches: a clause on each try, and a try around each run of statements. */
  private void write() {
    for (Map.Entry<TryStatement, List<Caught>> e : clauses.entrySet()) {
      TryStatement t = e.getKey();
      Signatures.Source s = tries.get(t);
      List<?> existing = t.catchClauses();
      int at =
          existing.isEmpty()
              ? Nodes.end(t.getBody())
              : Nodes.end((ASTNode) existing.get(existing.size() - 1));
      String indent = Lines.indentOf(s.text(), t.getStartPosition());
      StringBuilder b = new StringBuilder();
      for (Caught c : e.getValue()) {
        b.append(clause(c, freeIn(t), indent, step(s.text())));
      }
      change.replaceRange(s, at, at, b.toString());
    }
    // A try inside another the tool writes is dropped, its catches going to the outer one, and
    // tries around adjacent statements of one block become one: two would meet at one offset.
    List<Wrap> kept = new ArrayList<>();
    wraps.sort(Comparator.comparingInt(Wrap::start));
    for (Wrap w : wraps) {
      Wrap outer = null;
      for (Wrap k : kept) {
        if (k.s == w.s && k.start() <= w.end() && w.start() <= k.end()
            || k.block == w.block && k.to + 1 == w.from) {
          outer = k;
        }
      }
      if (outer == null) {
        kept.add(w);
        continue;
      }
      if (outer.block == w.block) {
        outer.from = Math.min(outer.from, w.from);
        outer.to = Math.max(outer.to, w.to);
      } else if (w.start() <= outer.start() && outer.end() <= w.end()) {
        kept.set(kept.indexOf(outer), w);
        w.catches.addAll(0, outer.catches);
        continue;
      }
      for (Caught c : w.catches) {
        if (!outer.catches.contains(c)) {
          outer.catches.add(c);
        }
      }
    }
    for (Wrap w : kept) {
      String text = w.s.text();
      int start = w.start();
      int end = w.end();
      int lineStart = Lines.start(text, start);
      int lineEnd = Lines.end(text, end - 1);
      String after = text.substring(end, lineEnd).strip();
      if (!text.substring(lineStart, start).isBlank()
          || !(after.isEmpty() || after.startsWith("//"))) {
        throw new Refusal(
            w.s.rel()
                + ", line "
                + w.s.unit().getLineNumber(start)
                + " shares its line with other code, so a try cannot go around it. Put the"
                + " statement on lines of its own first.");
      }
      for (int k = w.from; k <= w.to; k++) {
        if (!Nodes.all((ASTNode) w.block.statements().get(k), TextBlock.class).isEmpty()) {
          throw new Refusal(
              w.s.rel()
                  + ", line "
                  + w.s.unit().getLineNumber(start)
                  + " holds a text block, which indenting into a try would change.");
        }
      }
      String indent = Lines.indentOf(text, start);
      String step = step(text);
      change.replaceRange(w.s, lineStart, lineStart, indent + "try {\n" + step);
      for (int p = Lines.end(text, lineStart); p < lineEnd; p = Lines.end(text, p)) {
        if (!text.substring(p, Lines.end(text, p)).isBlank()) {
          change.replaceRange(w.s, p, p, step);
        }
      }
      StringBuilder b = new StringBuilder(indent).append("}");
      for (Caught c : w.catches) {
        b.append(clause(c, w.var, indent, step));
      }
      change.replaceRange(w.s, lineEnd, lineEnd, b.append("\n").toString());
    }
  }

  /** {@code " catch (X e) { throw new W(e); }"} on three lines, after a closing brace. */
  private static String clause(Caught c, String var, String indent, String step) {
    return " catch ("
        + c.name()
        + " "
        + var
        + ") {\n"
        + indent
        + step
        + "throw new "
        + c.wrapper()
        + "("
        + var
        + ");\n"
        + indent
        + "}";
  }

  /** A name for a catch's variable that the method around {@code n} does not use. */
  static String freeIn(ASTNode n) {
    ASTNode top = n;
    for (ASTNode p = n; p != null; p = p.getParent()) {
      if (p instanceof MethodDeclaration || p instanceof Initializer) {
        top = p;
      }
    }
    Set<String> used = new HashSet<>();
    for (SimpleName name : Nodes.all(top, SimpleName.class)) {
      used.add(name.getIdentifier());
    }
    String var = "e";
    for (int k = 2; used.contains(var); k++) {
      var = "e" + k;
    }
    return var;
  }

  static String step(String text) {
    return " ".repeat(Indent.step(text, 2));
  }

  private static String where(Site site) {
    return site.s().rel() + " line " + site.s().line(site.node());
  }
}
