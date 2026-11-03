package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeMethodReference;

/**
 * Signature changes: a parameter added, removed or moved in every declaration of a method's family
 * (what it overrides and what overrides it) and at every call in the project.
 */
final class Signatures {

  private Signatures() {}

  static Tools.Outcome addParameter(SourceProject project, Args args) throws Exception {
    String parameter = args.required("parameter");
    String type = args.required("type");
    String value = args.required("value");
    if (!SourceVersion.isName(parameter)) {
      throw new Refusal("parameter " + parameter + " is not a valid Java name.");
    }
    parsesAs("type", type, "class X { " + type + " x; }");
    parsesAs("value", value, "class X { Object x = " + value + "; }");
    Family family = Family.of(project, args);
    int at = family.index(args.string("before"), "before", true);
    family.refuseHiding(parameter);
    Change change = new Change(family);
    for (Decl d : family.decls) {
      change.insert(d.file(), d.m().parameters(), d.open(), -1, at, type + " " + parameter);
    }
    for (Call c : family.calls) {
      change.insert(c.file(), c.arguments(), c.open(), c.bare(), at, value);
    }
    return change.apply(
        project, "added " + type + " " + parameter + " to " + family.label, args.list("imports"));
  }

  static Tools.Outcome removeParameter(SourceProject project, Args args) throws Exception {
    Family family = Family.of(project, args);
    int at = family.index(args.required("parameter"), "parameter", false);
    family.refuseReading(at);
    for (Call c : family.calls) {
      Expression argument = (Expression) c.arguments().get(at);
      if (!pure(argument)) {
        throw new Refusal(
            "the argument "
                + argument
                + " ("
                + c.where(argument)
                + ") may have side effects, and removing it would drop them. Assign it to a"
                + " variable before the call, or remove the call.");
      }
    }
    Change change = new Change(family);
    for (Decl d : family.decls) {
      change.remove(d.file(), d.m().parameters(), at);
      change.removeParamTag(d, at);
    }
    for (Call c : family.calls) {
      change.remove(c.file(), c.arguments(), at);
    }
    return change.apply(
        project, "removed " + args.required("parameter") + " from " + family.label, List.of());
  }

  static Tools.Outcome moveParameter(SourceProject project, Args args) throws Exception {
    Family family = Family.of(project, args);
    String parameter = args.required("parameter");
    String before = args.string("before");
    int from = family.index(parameter, "parameter", false);
    int to = family.index(before, "before", true);
    if (to == from || to == from + 1) {
      throw new Refusal(parameter + " is already there; nothing to move.");
    }
    List<Integer> order = new ArrayList<>();
    for (int i = 0; i < family.size; i++) {
      order.add(i);
    }
    order.remove(from);
    order.add(to > from ? to - 1 : to, from);
    int low = Math.min(from, to);
    int high = Math.max(from, to - 1);
    for (Call c : family.calls) {
      List<Expression> impure = new ArrayList<>();
      for (int i = low; i <= high; i++) {
        Expression argument = (Expression) c.arguments().get(i);
        if (!pure(argument)) {
          impure.add(argument);
        }
      }
      if (impure.size() > 1) {
        throw new Refusal(
            "the arguments "
                + impure.get(0)
                + " and "
                + impure.get(1)
                + " ("
                + c.where(impure.get(0))
                + ") may both have side effects, and moving would change their order. Assign"
                + " one to a variable before the call first.");
      }
    }
    Change change = new Change(family);
    for (Decl d : family.decls) {
      change.reorder(d.file(), d.m().parameters(), order);
    }
    for (Call c : family.calls) {
      change.reorder(c.file(), c.arguments(), order);
    }
    return change.apply(
        project,
        "moved "
            + parameter
            + (before == null ? " to last" : " before " + before)
            + " in "
            + family.label,
        List.of());
  }

  /** Refuses {@code text} as the {@code what} argument unless {@code wrapped} parses. */
  private static void parsesAs(String what, String text, String wrapped) {
    for (IProblem p : Jdt.parseSyntax(wrapped).getProblems()) {
      if (p.isError()) {
        throw new Refusal(what + " " + text + " does not parse: " + p.getMessage() + ".");
      }
    }
  }

  /** A file of the project, read and parsed with bindings. */
  private record Source(Path file, String rel, String text, CompilationUnit unit) {
    int line(ASTNode node) {
      return unit.getLineNumber(node.getStartPosition());
    }
  }

  /** A declaration in the family; {@code open} is the offset just after its "(". */
  private record Decl(Source file, MethodDeclaration m, IMethodBinding binding, int open) {}

  /**
   * A call of the family: its arguments, the offset just after its "(", or -1 for an enum constant
   * without one, then {@code bare} is where "(...)" goes.
   */
  private record Call(Source file, ASTNode node, List<?> arguments, int open, int bare) {
    String where(ASTNode n) {
      return file.rel() + ", line " + file.line(n);
    }
  }

  /** The method, every declaration it overrides or that overrides it, and every call of them. */
  private static final class Family {
    final String label;
    final List<Decl> decls = new ArrayList<>();
    final List<Call> calls = new ArrayList<>();
    int size;
    MethodDeclaration target;

    private Family(String label) {
      this.label = label;
    }

    static Family of(SourceProject project, Args args) throws Exception {
      String rel = args.required("file");
      Path path = project.javaFile(rel);
      Map<Path, Source> sources = new LinkedHashMap<>();
      List<Path> files = project.javaFiles();
      Map<Path, CompilationUnit> units = Jdt.parseAll(project, files);
      for (Path f : files) {
        sources.put(f, new Source(f, project.relative(f), Files.readString(f), units.get(f)));
      }
      Source home = sources.get(path);
      if (home == null) {
        throw new Refusal(rel + " is not a Java file under a source root.");
      }
      Declarations.Named named = Declarations.find(home.unit(), args.required("name"), rel);
      if (!(named.decl() instanceof MethodDeclaration target)) {
        throw new Refusal(named.label() + " is not a method or constructor.");
      }
      IMethodBinding binding = target.resolveBinding();
      if (binding == null) {
        throw new Refusal(named.label() + " does not resolve; fix its compile errors first.");
      }
      if (binding.isVarargs()) {
        throw new Refusal(named.label() + " has varargs, which these tools do not change yet.");
      }
      Family family = new Family(named.label());
      family.target = target;
      family.size = target.parameters().size();

      Set<String> keys =
          family(
              project,
              sources.values().stream().map(Source::unit).toList(),
              binding,
              "so its parameters cannot change.");
      Map<String, Decl> declared = new HashMap<>();
      for (Source s : sources.values()) {
        s.unit()
            .accept(
                new ASTVisitor() {
                  @Override
                  public boolean visit(MethodDeclaration m) {
                    IMethodBinding b = m.resolveBinding();
                    if (b != null && keys.contains(key(b))) {
                      declared.put(key(b), new Decl(s, m, b, open(s.text(), m.getName())));
                    }
                    return true;
                  }
                });
      }
      for (String k : keys) {
        Decl d = declared.get(k);
        if (d != null) {
          family.decls.add(d);
        }
      }
      for (Source s : sources.values()) {
        family.findCalls(s, keys);
      }
      return family;
    }

    /** Adds the calls in {@code s} of the methods with {@code keys}; refuses method references. */
    private void findCalls(Source s, Set<String> keys) {
      String text = s.text();
      s.unit()
          .accept(
              new ASTVisitor() {
                boolean ours(IMethodBinding b) {
                  return b != null && keys.contains(key(b));
                }

                @Override
                public boolean visit(MethodInvocation n) {
                  if (ours(n.resolveMethodBinding())) {
                    calls.add(new Call(s, n, n.arguments(), open(text, n.getName()), -1));
                  }
                  return true;
                }

                @Override
                public boolean visit(SuperMethodInvocation n) {
                  if (ours(n.resolveMethodBinding())) {
                    calls.add(new Call(s, n, n.arguments(), open(text, n.getName()), -1));
                  }
                  return true;
                }

                @Override
                public boolean visit(ClassInstanceCreation n) {
                  if (ours(n.resolveConstructorBinding())) {
                    calls.add(new Call(s, n, n.arguments(), open(text, n.getType()), -1));
                  }
                  return true;
                }

                @Override
                public boolean visit(ConstructorInvocation n) {
                  if (ours(n.resolveConstructorBinding())) {
                    calls.add(new Call(s, n, n.arguments(), after(text, n.getStartPosition()), -1));
                  }
                  return true;
                }

                @Override
                public boolean visit(SuperConstructorInvocation n) {
                  if (ours(n.resolveConstructorBinding())) {
                    int from =
                        n.getExpression() == null
                            ? n.getStartPosition()
                            : n.getExpression().getStartPosition() + n.getExpression().getLength();
                    calls.add(new Call(s, n, n.arguments(), after(text, from), -1));
                  }
                  return true;
                }

                @Override
                public boolean visit(EnumConstantDeclaration n) {
                  if (ours(n.resolveConstructorBinding())) {
                    int end = n.getName().getStartPosition() + n.getName().getLength();
                    int i = end;
                    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                      i++;
                    }
                    boolean parens = i < text.length() && text.charAt(i) == '(';
                    calls.add(new Call(s, n, n.arguments(), parens ? i + 1 : -1, end));
                  }
                  return true;
                }

                private boolean reference(MethodReference n, IMethodBinding b) {
                  if (ours(b)) {
                    throw new Refusal(
                        label
                            + " is used as a method reference ("
                            + s.rel()
                            + ", line "
                            + s.line(n)
                            + "), whose functional interface fixes its parameters. Replace"
                            + " it with a lambda that calls it first.");
                  }
                  return true;
                }

                @Override
                public boolean visit(ExpressionMethodReference n) {
                  return reference(n, n.resolveMethodBinding());
                }

                @Override
                public boolean visit(SuperMethodReference n) {
                  return reference(n, n.resolveMethodBinding());
                }

                @Override
                public boolean visit(TypeMethodReference n) {
                  return reference(n, n.resolveMethodBinding());
                }

                @Override
                public boolean visit(CreationReference n) {
                  return reference(n, n.resolveMethodBinding());
                }
              });
    }

    /**
     * The index of the target's parameter {@code name}; with {@code orLast}, a null name is the end
     * of the list.
     */
    int index(String name, String what, boolean orLast) {
      if (name == null && orLast) {
        return size;
      }
      List<String> names = new ArrayList<>();
      for (Object o : target.parameters()) {
        names.add(((SingleVariableDeclaration) o).getName().getIdentifier());
      }
      int i = names.indexOf(name);
      if (i < 0) {
        throw new Refusal(
            label
                + " has no parameter "
                + name
                + " ("
                + what
                + "); its parameters are "
                + (names.isEmpty() ? "none" : String.join(", ", names))
                + ".");
      }
      return i;
    }

    /** Refuses when a body of the family reads its parameter at {@code at}. */
    void refuseReading(int at) {
      for (Decl d : decls) {
        if (d.m().getBody() == null) {
          continue;
        }
        SingleVariableDeclaration p = (SingleVariableDeclaration) d.m().parameters().get(at);
        IVariableBinding pb = p.resolveBinding();
        d.m()
            .getBody()
            .accept(
                new ASTVisitor() {
                  @Override
                  public boolean visit(SimpleName n) {
                    if (pb != null && pb.isEqualTo(n.resolveBinding())) {
                      throw new Refusal(
                          label(d.binding())
                              + " reads "
                              + p.getName().getIdentifier()
                              + " ("
                              + d.file().rel()
                              + ", line "
                              + d.file().line(n)
                              + "). Change the body so it no longer does, then remove it.");
                    }
                    return true;
                  }
                });
      }
    }

    /** Refuses when a body of the family reads a field named {@code name} without a qualifier. */
    void refuseHiding(String name) {
      for (Decl d : decls) {
        if (d.m().getBody() == null) {
          continue;
        }
        d.m()
            .getBody()
            .accept(
                new ASTVisitor() {
                  @Override
                  public boolean visit(SimpleName n) {
                    boolean qualified =
                        (n.getParent() instanceof FieldAccess f && f.getName() == n)
                            || (n.getParent() instanceof QualifiedName q && q.getName() == n);
                    if (n.getIdentifier().equals(name)
                        && !qualified
                        && n.resolveBinding() instanceof IVariableBinding v
                        && v.isField()) {
                      throw new Refusal(
                          label(d.binding())
                              + " reads the field "
                              + name
                              + " ("
                              + d.file().rel()
                              + ", line "
                              + d.file().line(n)
                              + "), which a parameter "
                              + name
                              + " would hide. Choose another name.");
                    }
                    return true;
                  }
                });
      }
    }
  }

  /** One text replacement in a file. */
  private record Edit(int start, int end, String text) {}

  /** The edits of one signature change, by file, applied together under the gate. */
  private static final class Change {
    final Family family;
    final Map<Source, List<Edit>> edits = new LinkedHashMap<>();

    Change(Family family) {
      this.family = family;
    }

    private void add(Source file, int start, int end, String text) {
      edits.computeIfAbsent(file, k -> new ArrayList<>()).add(new Edit(start, end, text));
    }

    /** Inserts {@code text} at position {@code at} of {@code list}, which opens at {@code open}. */
    void insert(Source file, List<?> list, int open, int bare, int at, String text) {
      if (list.isEmpty()) {
        if (open < 0) {
          add(file, bare, bare, "(" + text + ")");
        } else {
          add(file, open, open, text);
        }
      } else if (at == list.size()) {
        int end = end((ASTNode) list.get(list.size() - 1));
        add(file, end, end, ", " + text);
      } else {
        int start = ((ASTNode) list.get(at)).getStartPosition();
        add(file, start, start, text + ", ");
      }
    }

    /** Removes the element at {@code at} of {@code list} with its comma. */
    void remove(Source file, List<?> list, int at) {
      ASTNode e = (ASTNode) list.get(at);
      if (list.size() == 1) {
        add(file, e.getStartPosition(), end(e), "");
      } else if (at < list.size() - 1) {
        add(file, e.getStartPosition(), ((ASTNode) list.get(at + 1)).getStartPosition(), "");
      } else {
        add(file, end((ASTNode) list.get(at - 1)), end(e), "");
      }
    }

    /** Puts the elements of {@code list} in {@code order}, keeping the separators in place. */
    void reorder(Source file, List<?> list, List<Integer> order) {
      for (int k = 0; k < order.size(); k++) {
        if (order.get(k) != k) {
          ASTNode at = (ASTNode) list.get(k);
          ASTNode from = (ASTNode) list.get(order.get(k));
          add(
              file,
              at.getStartPosition(),
              end(at),
              file.text().substring(from.getStartPosition(), end(from)));
        }
      }
    }

    /** Removes the {@code @param} tag of the parameter at {@code at} from {@code d}'s Javadoc. */
    void removeParamTag(Decl d, int at) {
      Javadoc doc = d.m().getJavadoc();
      if (doc == null) {
        return;
      }
      String name =
          ((SingleVariableDeclaration) d.m().parameters().get(at)).getName().getIdentifier();
      String text = d.file().text();
      for (Object o : doc.tags()) {
        TagElement tag = (TagElement) o;
        if ("@param".equals(tag.getTagName())
            && !tag.fragments().isEmpty()
            && tag.fragments().get(0) instanceof SimpleName n
            && n.getIdentifier().equals(name)) {
          int start = text.lastIndexOf('\n', tag.getStartPosition()) + 1;
          int end = text.indexOf('\n', end(tag)) + 1;
          add(d.file(), start, end, "");
        }
      }
    }

    /** Applies the edits, fixes each file's imports, and writes them all under the gate. */
    Tools.Outcome apply(SourceProject project, String what, List<String> chosen) throws Exception {
      Map<Path, String> texts = new LinkedHashMap<>();
      Set<String> files = new TreeSet<>();
      List<String> notes = new ArrayList<>();
      for (Map.Entry<Source, List<Edit>> e : edits.entrySet()) {
        Source s = e.getKey();
        List<Edit> list = new ArrayList<>(e.getValue());
        list.sort((a, b) -> a.start() != b.start() ? b.start() - a.start() : b.end() - a.end());
        StringBuilder text = new StringBuilder(s.text());
        int limit = Integer.MAX_VALUE;
        for (Edit edit : list) {
          if (edit.end() > limit) {
            throw new Refusal(
                "calls of "
                    + family.label
                    + " are nested in each other's arguments in "
                    + s.rel()
                    + " (line "
                    + s.unit().getLineNumber(edit.start())
                    + "). Assign the inner call to a variable first.");
          }
          text.replace(edit.start(), edit.end(), edit.text());
          limit = edit.start();
        }
        Imports.Fixed fixed = Imports.fix(project, s.file(), s.text(), text.toString(), chosen);
        texts.put(s.file(), fixed.text());
        files.add(s.rel());
        if (!fixed.notes().isEmpty()) {
          notes.add(s.rel() + ": " + String.join("; ", fixed.notes()));
        }
      }
      Stubs.Declared stubs =
          Gate.applyOrRefuse(
              project,
              texts,
              what,
              "Nothing was changed. If a value or type does not resolve at some call, choose one"
                  + " that does.");
      int d = family.decls.size();
      int c = family.calls.size();
      return new Tools.Outcome(
          what
              + " in "
              + d
              + (d == 1 ? " declaration" : " declarations")
              + " and "
              + c
              + (c == 1 ? " call" : " calls")
              + " ("
              + String.join(", ", files)
              + ")"
              + (notes.isEmpty() ? "" : "; " + String.join("; ", notes)),
          stubs);
    }
  }

  /**
   * The binding keys of {@code binding}'s family in {@code units}: every declaration it overrides
   * or that overrides it, transitively. Refuses when one of them overrides a library method:
   * "{@code Name.compareTo(Name) overrides compareTo in java.lang.Comparable, which is not the
   * project's, }" then {@code consequence}.
   */
  static Set<String> family(
      SourceProject project,
      Collection<CompilationUnit> units,
      IMethodBinding binding,
      String consequence) {
    Map<String, Set<String>> links = new HashMap<>();
    Map<String, IMethodBinding> declared = new HashMap<>();
    Map<String, IMethodBinding> library = new HashMap<>();
    for (CompilationUnit unit : units) {
      unit.accept(
          new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration m) {
              IMethodBinding b = m.resolveBinding();
              if (b != null) {
                String k = key(b);
                declared.put(k, b);
                for (IMethodBinding over : overridden(b)) {
                  String o = key(over);
                  links.computeIfAbsent(k, x -> new HashSet<>()).add(o);
                  links.computeIfAbsent(o, x -> new HashSet<>()).add(k);
                  if (!projects(project, over.getDeclaringClass())) {
                    library.put(k, over);
                  }
                }
              }
              return true;
            }
          });
    }
    Set<String> keys = new TreeSet<>();
    Deque<String> todo = new ArrayDeque<>(List.of(key(binding)));
    while (!todo.isEmpty()) {
      String k = todo.pop();
      if (keys.add(k)) {
        todo.addAll(links.getOrDefault(k, Set.of()));
      }
    }
    for (String k : keys) {
      IMethodBinding over = library.get(k);
      if (over != null) {
        throw new Refusal(
            label(declared.get(k))
                + " overrides "
                + over.getName()
                + " in "
                + over.getDeclaringClass().getErasure().getQualifiedName()
                + ", which is not the project's, "
                + consequence);
      }
    }
    return keys;
  }

  /** The methods of supertypes that {@code b} overrides. */
  private static List<IMethodBinding> overridden(IMethodBinding b) {
    List<IMethodBinding> out = new ArrayList<>();
    if (b.isConstructor() || b.getDeclaringClass() == null) {
      return out;
    }
    Deque<ITypeBinding> todo = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    ITypeBinding start = b.getDeclaringClass();
    if (start.getSuperclass() != null) {
      todo.add(start.getSuperclass());
    }
    todo.addAll(List.of(start.getInterfaces()));
    while (!todo.isEmpty()) {
      ITypeBinding t = todo.pop();
      if (!seen.add(t.getErasure().getKey())) {
        continue;
      }
      for (IMethodBinding m : t.getDeclaredMethods()) {
        if (b.overrides(m)) {
          out.add(m);
        }
      }
      if (t.getSuperclass() != null) {
        todo.add(t.getSuperclass());
      }
      todo.addAll(List.of(t.getInterfaces()));
    }
    return out;
  }

  /** Whether {@code type} is declared in one of the project's source files. */
  static boolean projects(SourceProject project, ITypeBinding type) {
    ITypeBinding top = type.getErasure();
    while (top.getDeclaringClass() != null) {
      top = top.getDeclaringClass();
    }
    String path = top.getQualifiedName().replace('.', '/') + ".java";
    for (Path root : project.sourceRoots()) {
      if (Files.isRegularFile(root.resolve(path))) {
        return true;
      }
    }
    return false;
  }

  /** The key that names {@code b}'s declaration in every file's bindings. */
  private static String key(IMethodBinding b) {
    return b.getMethodDeclaration().getKey();
  }

  /** "Cart.add(String, int)". */
  static String label(IMethodBinding b) {
    List<String> types = new ArrayList<>();
    for (ITypeBinding t : b.getMethodDeclaration().getParameterTypes()) {
      types.add(t.getErasure().getName());
    }
    return b.getDeclaringClass().getErasure().getName()
        + "."
        + b.getName()
        + "("
        + String.join(", ", types)
        + ")";
  }

  /** Whether evaluating {@code e} can have no side effect: names, literals, this, and the like. */
  private static boolean pure(Expression e) {
    return switch (e) {
      case Name n -> true;
      case NumberLiteral n -> true;
      case StringLiteral n -> true;
      case CharacterLiteral n -> true;
      case BooleanLiteral n -> true;
      case NullLiteral n -> true;
      case TypeLiteral n -> true;
      case TextBlock n -> true;
      case ThisExpression n -> true;
      case LambdaExpression n -> true;
      case MethodReference n -> true;
      case FieldAccess f -> pure(f.getExpression());
      case ParenthesizedExpression p -> pure(p.getExpression());
      case CastExpression c -> pure(c.getExpression());
      case InstanceofExpression i -> pure(i.getLeftOperand());
      case PrefixExpression p ->
          p.getOperator() != PrefixExpression.Operator.INCREMENT
              && p.getOperator() != PrefixExpression.Operator.DECREMENT
              && pure(p.getOperand());
      case InfixExpression i ->
          pure(i.getLeftOperand()) && pure(i.getRightOperand()) && allPure(i.extendedOperands());
      case ConditionalExpression c ->
          pure(c.getExpression()) && pure(c.getThenExpression()) && pure(c.getElseExpression());
      default -> false;
    };
  }

  private static boolean allPure(List<?> expressions) {
    for (Object o : expressions) {
      if (!pure((Expression) o)) {
        return false;
      }
    }
    return true;
  }

  /** The offset just after the first "(" following {@code node}. */
  private static int open(String text, ASTNode node) {
    return after(text, end(node));
  }

  /** The offset just after the first "(" at or after {@code from}. */
  private static int after(String text, int from) {
    return text.indexOf('(', from) + 1;
  }

  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }
}
