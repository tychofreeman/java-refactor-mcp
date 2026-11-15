package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * What calling a method can do that other code sees, as gorefactor's outline says it: the first
 * side effect its body has, in source order. A side effect is an assignment to a field, a static,
 * or an array or object the method did not make itself; a call of a library method not in {@link
 * #pure}; or a call of a project method whose body has one, read from its file and summarized once.
 * A call back into a method being summarized is not known, so it counts. Lambdas and anonymous
 * classes in a body count as part of it. A call runs the method it names: overrides are not
 * followed.
 */
final class SideEffects {

  /** One thing a body does, in order: a side effect of its own, or a call of a project method. */
  private sealed interface Event {}

  private record Own(String what) implements Event {}

  /**
   * A call of the project method {@code key}, written {@code name}, named {@code method} (a
   * constructor by its class), declared in {@code file}.
   */
  private record Call(String key, String name, String method, Path file) implements Event {}

  private final SourceProject project;
  private final Map<String, List<Event>> bodies = new HashMap<>();
  private final Map<Path, Set<String>> read = new HashMap<>();
  private final Map<Path, Set<String>> missing = new LinkedHashMap<>();
  private final Map<String, String> known = new HashMap<>();
  private final Set<String> active = new HashSet<>();
  private final Set<Path> checked = new HashSet<>();

  private SideEffects(SourceProject project) {
    this.project = project;
  }

  /**
   * For each method and constructor with a body in {@code unit} (parsed with bindings), "no side
   * effects" or "side effects: " and the first one, as gorefactor's outline gives them. The project
   * methods they call are read from their files as needed, each file parsed with only the bodies of
   * the methods of those names.
   */
  static Map<MethodDeclaration, String> of(SourceProject project, CompilationUnit unit) {
    SideEffects s = new SideEffects(project);
    if (Jdt.fileOf(unit) != null) {
      s.read.put(Jdt.fileOf(unit), Set.of(ALL));
    }
    Map<MethodDeclaration, String> keys = s.collect(unit, Set.of(ALL));
    if (Jdt.fileOf(unit) != null && Jdt.sourceOf(unit) != null) {
      s.remember(Jdt.fileOf(unit), Jdt.sourceOf(unit), Set.of(ALL), keys.values());
    }
    while (true) {
      s.missing.clear();
      for (String key : keys.values()) {
        s.summary(key);
      }
      if (s.missing.isEmpty()) {
        break;
      }
      s.read(new LinkedHashMap<>(s.missing));
    }
    Map<MethodDeclaration, String> out = new LinkedHashMap<>();
    keys.forEach((declaration, key) -> {
      String found = s.known.get(key);
      if (found != null) {
        out.put(declaration, found.isEmpty() ? "no side effects" : "side effects: " + found);
      }
    });
    return out;
  }

  /**
   * Records the events of each method of {@code unit} with a body and one of {@code names} ({@link
   * #ALL} for every one); returns their keys.
   */
  private Map<MethodDeclaration, String> collect(CompilationUnit unit, Set<String> names) {
    Map<MethodDeclaration, String> keys = new LinkedHashMap<>();
    for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
      IMethodBinding b = m.resolveBinding();
      String key = Bindings.declarationKey(b);
      if (m.getBody() != null
          && key != null
          && (names.contains(ALL) || names.contains(m.getName().getIdentifier()))) {
        bodies.put(key, events(m, b));
        keys.put(m, key);
      }
    }
    return keys;
  }

  /**
   * The side effect of the method {@code key} ("" for none), or null while a method it calls is in
   * a file not yet read, which is then in {@link #missing}.
   */
  private String summary(String key) {
    String found = known.get(key);
    if (found != null) {
      return found;
    }
    active.add(key);
    found = "";
    boolean pending = false;
    for (Event e : bodies.get(key)) {
      String what = e instanceof Own own ? own.what() : callee((Call) e);
      if (what == null) {
        pending = true;
      } else if (!what.isEmpty()) {
        found = what;
        break;
      }
    }
    active.remove(key);
    if (pending) {
      return null;
    }
    known.put(key, found);
    return found;
  }

  /** What the call {@code c} does, as {@link #summary} says it. */
  private String callee(Call c) {
    if (active.contains(c.key())) {
      return "calls " + c.name();
    }
    if (!bodies.containsKey(c.key())) {
      cached(c.file());
    }
    if (!bodies.containsKey(c.key())) {
      Set<String> names = c.file() == null ? null : read.get(c.file());
      if (c.file() != null
          && (names == null || !names.contains(ALL) && !names.contains(c.method()))) {
        missing.computeIfAbsent(c.file(), f -> new HashSet<>()).add(c.method());
        return null;
      }
      return "calls " + c.name();
    }
    String s = summary(c.key());
    return s == null || s.isEmpty() ? s : "calls " + c.name() + ", which " + s;
  }

  /** What {@code m}'s body does, in source order: its own side effects, and its project calls. */
  private List<Event> events(MethodDeclaration m, IMethodBinding binding) {
    List<Event> out = new ArrayList<>();
    Set<String> fresh = fresh(m);
    ITypeBinding constructing =
        m.isConstructor() && binding != null ? binding.getDeclaringClass() : null;
    m.getBody()
        .accept(
            new ASTVisitor() {
              @Override
              public boolean visit(TypeDeclarationStatement n) {
                return false;
              }

              @Override
              public void preVisit(ASTNode n) {
                switch (n) {
                  case Assignment a -> write(a.getLeftHandSide());
                  case PostfixExpression p -> write(p.getOperand());
                  case PrefixExpression p
                      when p.getOperator() == PrefixExpression.Operator.INCREMENT
                          || p.getOperator() == PrefixExpression.Operator.DECREMENT ->
                      write(p.getOperand());
                  case MethodInvocation c -> call(c);
                  case SuperMethodInvocation c -> call(c);
                  case ClassInstanceCreation c -> call(c);
                  case ConstructorInvocation c -> call(c);
                  case SuperConstructorInvocation c -> call(c);
                  case MethodReference c -> call(c);
                  default -> {}
                }
              }

              private void write(Expression target) {
                Expression t = Nodes.unparenthesized(target);
                if (!ownWrite(t, fresh, constructing)) {
                  out.add(new Own("assigns to " + Nodes.show(t)));
                }
              }

              private void call(ASTNode n) {
                IMethodBinding b = Calls.binding(n);
                String name = callName(n);
                if (b == null) {
                  out.add(new Own("calls " + name));
                  return;
                }
                IMethodBinding declared = b.getMethodDeclaration();
                ITypeBinding owner = declared.getDeclaringClass();
                if (!owner.isFromSource()) {
                  if (!pure(n, declared, fresh)) {
                    out.add(new Own("calls " + name));
                  }
                } else if (declared.isDefaultConstructor()
                    || declared.isSyntheticRecordMethod()
                    || owner.isEnum()
                        && (declared.getName().equals("values")
                            || declared.getName().equals("valueOf"))) {
                  return;
                } else if (Modifier.isAbstract(declared.getModifiers())
                    || Modifier.isNative(declared.getModifiers())
                    || owner.isInterface()
                        && !Modifier.isDefault(declared.getModifiers())
                        && !Modifier.isStatic(declared.getModifiers())) {
                  out.add(new Own("calls " + name));
                } else {
                  out.add(
                      new Call(
                          Bindings.declarationKey(declared),
                          name,
                          declared.getName(),
                          project.fileOf(owner)));
                }
              }
            });
    return out;
  }

  /**
   * Whether writing {@code t} changes only what the method owns: a local or a parameter, an element
   * or field of a local that only ever holds what the method made ({@code fresh}), a field of a
   * local or anonymous class, or, in a constructor, a field of the object it makes.
   */
  private static boolean ownWrite(Expression t, Set<String> fresh, ITypeBinding constructing) {
    return switch (t) {
      case SimpleName s ->
          s.resolveBinding() instanceof IVariableBinding v
              && (!v.isField() || ownField(v, constructing));
      case FieldAccess f ->
          f.getExpression() instanceof ThisExpression self
                  && self.getQualifier() == null
                  && ownField(f.resolveFieldBinding(), constructing)
              || isFresh(f.getExpression(), fresh);
      case SuperFieldAccess f -> ownField(f.resolveFieldBinding(), constructing);
      case QualifiedName q -> isFresh(q.getQualifier(), fresh);
      case ArrayAccess a -> isFresh(a.getArray(), fresh);
      default -> false;
    };
  }

  /**
   * Whether {@code field} belongs to an object the code made: one of a local or anonymous class, or
   * an instance field of the class {@code constructing} constructs.
   */
  private static boolean ownField(IVariableBinding field, ITypeBinding constructing) {
    if (field == null || !field.isField() || field.getDeclaringClass() == null) {
      return false;
    }
    ITypeBinding owner = field.getDeclaringClass();
    return owner.isLocal()
        || owner.isAnonymous()
        || constructing != null
            && !Modifier.isStatic(field.getModifiers())
            && Hierarchy.isSubtype(constructing, owner);
  }

  /** Whether {@code e} names a local of {@link #fresh}. */
  private static boolean isFresh(Expression e, Set<String> fresh) {
    return e != null
        && Nodes.unparenthesized(e) instanceof SimpleName s
        && s.resolveBinding() instanceof IVariableBinding v
        && !v.isField()
        && fresh.contains(v.getKey());
  }

  /**
   * The keys of the locals of {@code m} (not its parameters) that only ever hold what it made: each
   * value given them is a new object or a new array.
   */
  private static Set<String> fresh(MethodDeclaration m) {
    Map<String, Boolean> made = new HashMap<>();
    for (VariableDeclarationFragment f :
        Nodes.all(m.getBody(), VariableDeclarationFragment.class)) {
      IVariableBinding v = f.resolveBinding();
      if (v != null && !v.isField()) {
        made.merge(
            v.getKey(),
            f.getInitializer() == null || isNew(f.getInitializer()),
            Boolean::logicalAnd);
      }
    }
    for (Assignment a : Nodes.all(m.getBody(), Assignment.class)) {
      if (Nodes.unparenthesized(a.getLeftHandSide()) instanceof SimpleName s
          && s.resolveBinding() instanceof IVariableBinding v
          && made.containsKey(v.getKey())) {
        made.put(
            v.getKey(),
            made.get(v.getKey())
                && a.getOperator() == Assignment.Operator.ASSIGN
                && isNew(a.getRightHandSide()));
      }
    }
    Set<String> out = new HashSet<>();
    made.forEach((key, isMade) -> {
      if (isMade) {
        out.add(key);
      }
    });
    return out;
  }

  private static boolean isNew(Expression e) {
    Expression x = Nodes.unparenthesized(e);
    return x instanceof ClassInstanceCreation
        || x instanceof ArrayCreation
        || x instanceof ArrayInitializer;
  }

  /** The call {@code n} as a side effect names it: what it calls, as written. */
  private static String callName(ASTNode n) {
    return switch (n) {
      case MethodInvocation m ->
          (m.getExpression() == null ? "" : Nodes.show(m.getExpression()) + ".")
              + m.getName().getIdentifier();
      case SuperMethodInvocation m -> "super." + m.getName().getIdentifier();
      case ClassInstanceCreation c -> "new " + Nodes.show(c.getType());
      case ConstructorInvocation c -> "this";
      case SuperConstructorInvocation c -> "super";
      default -> Nodes.show(n);
    };
  }

  /** Library types whose every method and constructor has no side effect. */
  private static final Set<String> PURE_TYPES =
      Set.of(
          "java.lang.String",
          "java.lang.CharSequence",
          "java.lang.Math",
          "java.lang.StrictMath",
          "java.lang.Integer",
          "java.lang.Long",
          "java.lang.Short",
          "java.lang.Byte",
          "java.lang.Double",
          "java.lang.Float",
          "java.lang.Character",
          "java.lang.Boolean",
          "java.lang.Number",
          "java.lang.Enum",
          "java.lang.Record",
          "java.util.Objects",
          "java.util.Optional",
          "java.util.OptionalInt",
          "java.util.OptionalLong",
          "java.util.OptionalDouble",
          "java.util.stream.Collectors",
          "java.util.regex.Pattern",
          "java.util.regex.Matcher",
          "java.math.BigInteger",
          "java.math.BigDecimal",
          "java.nio.charset.Charset",
          "java.util.stream.StreamSupport");

  /** Library methods with no side effect, by their type. */
  private static final Map<String, Set<String>> PURE_METHODS =
      Map.of(
          "java.lang.Object",
          Set.of("equals", "hashCode", "toString", "getClass"),
          "java.lang.Throwable",
          Set.of("getMessage", "getLocalizedMessage", "getCause", "getStackTrace", "getSuppressed"),
          "java.lang.Class",
          Set.of(
              "getName",
              "getSimpleName",
              "getCanonicalName",
              "getTypeName",
              "getPackageName",
              "isInstance",
              "isAssignableFrom",
              "isArray",
              "isPrimitive",
              "isInterface",
              "isEnum",
              "isRecord",
              "getComponentType",
              "getSuperclass",
              "getInterfaces",
              "getEnclosingClass",
              "getDeclaringClass",
              "getModifiers",
              "cast"),
          "java.lang.System",
          Set.of(
              "identityHashCode",
              "lineSeparator",
              "nanoTime",
              "currentTimeMillis",
              "getProperty",
              "getenv"),
          "java.util.Arrays",
          Set.of(
              "asList",
              "copyOf",
              "copyOfRange",
              "equals",
              "deepEquals",
              "hashCode",
              "deepHashCode",
              "toString",
              "deepToString",
              "stream",
              "binarySearch",
              "mismatch",
              "compare"),
          "java.util.Collections",
          Set.of(
              "emptyList",
              "emptySet",
              "emptyMap",
              "emptyIterator",
              "singletonList",
              "singleton",
              "singletonMap",
              "unmodifiableList",
              "unmodifiableSet",
              "unmodifiableMap",
              "unmodifiableCollection",
              "unmodifiableSortedSet",
              "unmodifiableSortedMap",
              "nCopies",
              "max",
              "min",
              "frequency",
              "disjoint",
              "reverseOrder"),
          "java.lang.reflect.Array",
          Set.of(
              "getLength",
              "get",
              "getBoolean",
              "getByte",
              "getChar",
              "getShort",
              "getInt",
              "getLong",
              "getFloat",
              "getDouble",
              "newInstance"),
          "java.util.Comparator",
          Set.of(
              "comparing",
              "comparingInt",
              "comparingLong",
              "comparingDouble",
              "naturalOrder",
              "reverseOrder",
              "nullsFirst",
              "nullsLast",
              "thenComparing",
              "thenComparingInt",
              "thenComparingLong",
              "thenComparingDouble",
              "reversed"));

  /** The methods of a collection, map, iterator or entry that read it. */
  private static final Set<String> READS =
      Set.of(
          "get",
          "size",
          "isEmpty",
          "contains",
          "containsAll",
          "containsKey",
          "containsValue",
          "getOrDefault",
          "indexOf",
          "lastIndexOf",
          "keySet",
          "values",
          "entrySet",
          "iterator",
          "listIterator",
          "hasNext",
          "next",
          "hasPrevious",
          "previous",
          "nextIndex",
          "previousIndex",
          "subList",
          "getFirst",
          "getLast",
          "peek",
          "peekFirst",
          "peekLast",
          "element",
          "first",
          "last",
          "firstKey",
          "lastKey",
          "firstEntry",
          "lastEntry",
          "headMap",
          "tailMap",
          "subMap",
          "headSet",
          "tailSet",
          "floor",
          "ceiling",
          "higher",
          "lower",
          "floorKey",
          "ceilingKey",
          "higherKey",
          "lowerKey",
          "floorEntry",
          "ceilingEntry",
          "higherEntry",
          "lowerEntry",
          "descendingSet",
          "descendingMap",
          "navigableKeySet",
          "descendingKeySet",
          "comparator",
          "stream",
          "parallelStream",
          "toArray",
          "equals",
          "hashCode",
          "toString",
          "getKey",
          "getValue",
          "of",
          "copyOf",
          "entry",
          "ofEntries",
          "forEach",
          "spliterator",
          "reversed");

  /** The library types {@link #READS} applies to: collections, maps, iterators, entries. */
  private static final List<String> COLLECTIONS =
      List.of("java.lang.Iterable", "java.util.Map", "java.util.Iterator", "java.util.Map.Entry");

  /**
   * Library methods that write into one argument, by their type and name, and its position: no side
   * effect when that argument is a local of {@link #fresh}.
   */
  private static final Map<String, Integer> FILLS =
      Map.of(
          "java.lang.System.arraycopy", 2,
          "java.util.Arrays.fill", 0,
          "java.util.Arrays.sort", 0,
          "java.util.Arrays.setAll", 0,
          "java.util.Collections.sort", 0,
          "java.util.Collections.reverse", 0);

  /** Marks a method name that every method has, for a file read whole. */
  private static final String ALL = "*";

  /**
   * Whether the library call {@code n} of {@code m} has no side effect: a method of {@link
   * #PURE_TYPES}, {@link #PURE_METHODS}, a read of a collection, any method of a stream (what its
   * lambdas do is the body's), a new collection, string builder or exception, a call on a local of
   * {@code fresh}, or one of {@link #FILLS} into one.
   */
  private static boolean pure(ASTNode n, IMethodBinding m, Set<String> fresh) {
    ITypeBinding owner = m.getDeclaringClass().getErasure();
    String type = owner.getQualifiedName();
    String name = m.getName();
    if (PURE_TYPES.contains(type) || type.startsWith("java.time.")) {
      return true;
    }
    if (m.isConstructor()) {
      return type.equals("java.lang.Object")
          || type.equals("java.lang.StringBuilder")
          || type.equals("java.lang.StringBuffer")
          || type.equals("java.util.StringJoiner")
          || Hierarchy.isA(owner, "java.lang.Throwable")
          || COLLECTIONS.stream().anyMatch(c -> Hierarchy.isA(owner, c));
    }
    if (PURE_METHODS.getOrDefault(type, Set.of()).contains(name)
        || READS.contains(name) && COLLECTIONS.stream().anyMatch(c -> Hierarchy.isA(owner, c))
        || Hierarchy.isA(owner, "java.util.stream.BaseStream")) {
      return true;
    }
    if (n instanceof MethodInvocation call) {
      Integer into = FILLS.get(type + "." + name);
      return !Modifier.isStatic(m.getModifiers()) && isFresh(builder(call.getExpression()), fresh)
          || into != null
              && into < call.arguments().size()
              && isFresh((Expression) call.arguments().get(into), fresh);
    }
    return false;
  }

  /**
   * What a chain of calls that each return their receiver's type starts from: {@code out} for
   * {@code out.append(a).append(b)}, a builder's calls returning the builder.
   */
  private static Expression builder(Expression e) {
    Expression x = e == null ? null : Nodes.unparenthesized(e);
    while (x instanceof MethodInvocation call
        && call.getExpression() != null
        && call.resolveMethodBinding() != null
        && call.getExpression().resolveTypeBinding() != null
        && !Modifier.isStatic(call.resolveMethodBinding().getModifiers())
        && call.resolveMethodBinding()
            .getReturnType()
            .getErasure()
            .isEqualTo(call.getExpression().resolveTypeBinding().getErasure())) {
      x = Nodes.unparenthesized(call.getExpression());
    }
    return x;
  }

  /**
   * Parses {@code files} again, in one batch, each with the bodies of the methods of the names
   * given it, and of those of its methods they call by name, and records those methods' events, for
   * the session too. Files that cannot be read or parsed are not followed: calls into them count.
   */
  private void read(Map<Path, Set<String>> files) {
    Map<Path, String> originals = new LinkedHashMap<>();
    Map<Path, String> texts = new LinkedHashMap<>();
    files.forEach((file, names) -> {
      Set<String> wanted = read.computeIfAbsent(file, f -> new HashSet<>());
      wanted.addAll(names);
      try {
        String text = Files.readString(file);
        CompilationUnit syntax = Jdt.parseSyntax(text);
        originals.put(file, text);
        texts.put(file, trimmed(text, syntax, wanted(syntax, wanted)));
      } catch (IOException e) {
        wanted.add(ALL);
      }
    });
    try {
      Jdt.parseTexts(
          project,
          texts,
          (file, unit) ->
              remember(
                  file,
                  originals.get(file),
                  read.get(file),
                  collect(unit, read.get(file)).values()));
    } catch (IOException | RuntimeException e) {
      texts.keySet().forEach(file -> read.get(file).add(ALL));
    }
  }

  /**
   * {@code text} with the body of each method of {@code unit} (its syntax) not named in {@code
   * names} emptied (its characters but line breaks made spaces), so a parse resolves only the
   * bodies wanted and keeps every offset and line.
   */
  private static String trimmed(String text, CompilationUnit unit, Set<String> names) {
    char[] out = text.toCharArray();
    for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
      if (m.getBody() != null
          && !names.contains(m.getName().getIdentifier())
          && Nodes.ancestor(m, MethodDeclaration.class) == null) {
        for (int i = m.getBody().getStartPosition() + 1; i < Nodes.end(m.getBody()) - 1; i++) {
          if (out[i] != '\n' && out[i] != '\r') {
            out[i] = ' ';
          }
        }
      }
    }
    return new String(out);
  }

  /**
   * {@code names} with the names of the methods and constructors of {@code unit} that the methods
   * of those names call, without a receiver or on {@code this} or one of its types, and so on.
   */
  private static Set<String> wanted(CompilationUnit unit, Set<String> names) {
    Set<String> types = new HashSet<>();
    for (AbstractTypeDeclaration t : Nodes.all(unit, AbstractTypeDeclaration.class)) {
      types.add(t.getName().getIdentifier());
    }
    List<MethodDeclaration> methods = new ArrayList<>();
    for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
      if (m.getBody() != null && Nodes.ancestor(m, MethodDeclaration.class) == null) {
        methods.add(m);
      }
    }
    boolean grew = true;
    while (grew) {
      grew = false;
      for (MethodDeclaration m : methods) {
        if (names.contains(m.getName().getIdentifier())) {
          for (ASTNode n : Nodes.all(m.getBody(), ASTNode.class)) {
            String called =
                switch (n) {
                  case MethodInvocation c
                      when c.getExpression() == null
                          || c.getExpression() instanceof ThisExpression
                          || c.getExpression() instanceof SimpleName s
                              && types.contains(s.getIdentifier()) ->
                      c.getName().getIdentifier();
                  case SuperMethodInvocation c -> c.getName().getIdentifier();
                  case ClassInstanceCreation c ->
                      Names.simpleName(c.getType().toString().replaceAll("<.*", ""));
                  case ConstructorInvocation c -> m.getName().getIdentifier();
                  default -> null;
                };
            if (called != null && (types.contains(called) || isMethod(methods, called))) {
              grew |= names.add(called);
            }
          }
        }
      }
    }
    return names;
  }

  private static boolean isMethod(List<MethodDeclaration> methods, String name) {
    return methods.stream().anyMatch(m -> m.getName().getIdentifier().equals(name));
  }

  /**
   * What the outline read from one file, which the session keeps ({@link Session#sideEffects}): the
   * hash of the file's text, the names of the methods whose events it holds ({@link #ALL} for every
   * one), and their events by method key.
   */
  static final class Read {
    private final String hash;
    private final Set<String> names;
    private final Map<String, List<Event>> bodies;

    private Read(String hash, Set<String> names, Map<String, List<Event>> bodies) {
      this.hash = hash;
      this.names = names;
      this.bodies = bodies;
    }
  }

  /** Keeps for the session the events of the methods {@code keys} read from {@code file}. */
  private void remember(Path file, String text, Set<String> names, Collection<String> keys) {
    Map<String, List<Event>> events = new HashMap<>();
    for (String key : keys) {
      events.put(key, bodies.get(key));
    }
    Read before = project.session().sideEffects.get(file);
    if (before != null && before.hash.equals(hash(text))) {
      events.putAll(before.bodies);
    }
    project.session().sideEffects.put(file, new Read(hash(text), Set.copyOf(names), events));
  }

  /**
   * Takes what the session read from {@code file} before, once a run, when the file's text has not
   * changed since; drops it when it has.
   */
  private void cached(Path file) {
    if (file == null || !checked.add(file) || read.containsKey(file)) {
      return;
    }
    Read kept = project.session().sideEffects.get(file);
    if (kept == null) {
      return;
    }
    try {
      if (kept.hash.equals(hash(Files.readString(file)))) {
        bodies.putAll(kept.bodies);
        read.put(file, new HashSet<>(kept.names));
        return;
      }
    } catch (IOException e) {
      // Read again below, as a file not kept.
    }
    project.session().sideEffects.remove(file);
  }

  private static String hash(String text) {
    return Hashes.sha256Hex(text.getBytes(StandardCharsets.UTF_8));
  }
}
