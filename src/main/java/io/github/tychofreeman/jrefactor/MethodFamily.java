package io.github.tychofreeman.jrefactor;

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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TypeMethodReference;

/** The method, every declaration it overrides or that overrides it, and every call of them. */
final class MethodFamily {
  final String label;
  final List<Decl> decls = new ArrayList<>();
  final List<Call> calls = new ArrayList<>();

  /** The lambdas that implement a member of the family. */
  final List<Lambda> lambdas = new ArrayList<>();

  int size;
  MethodDeclaration target;

  /** The files parsed, by path: where a declaration found later is edited. */
  final Map<Path, Source> sources = new LinkedHashMap<>();

  /** With {@link #keepReferences}, the method references that implement a member of the family. */
  final List<Reference> references = new ArrayList<>();

  /** Whether a method reference is kept in {@link #references}, not refused. */
  private boolean keepReferences;

  private MethodFamily(String label) {
    this.label = label;
  }

  static MethodFamily of(SourceProject project, Args args) throws Exception {
    return of(project, args, false, "so its parameters cannot change.", false);
  }

  /**
   * The family of the method {@code args} name. With {@code references}, a method reference is not
   * refused: one that implements the family is kept. {@code consequence} ends the refusal of a
   * method that overrides a library's. A varargs method is refused unless {@code varargs}: the
   * tools that rewrite calls by position do not change one yet.
   */
  static MethodFamily of(
      SourceProject project, Args args, boolean references, String consequence, boolean varargs)
      throws Exception {
    String rel = args.required("file");
    Path path = project.javaFile(rel);
    // A family member and a call of one have the method's simple name, so a file without that
    // name cannot hold either: only the files with it are parsed.
    Holding holding = Holding.of(project, simpleName(args.required("name")), path);
    Map<Path, Source> sources = new LinkedHashMap<>();
    for (Map.Entry<Path, CompilationUnit> e : holding.units().entrySet()) {
      Path f = e.getKey();
      sources.put(f, new Source(f, project.relative(f), holding.texts().get(f), e.getValue()));
    }
    Source home = sources.get(path);
    if (home == null) {
      throw new Refusal(rel + " is not a Java file under a source root.");
    }
    Declarations.Named named = Declarations.find(home.unit(), args.required("name"), rel);
    if (named.decl() instanceof AbstractTypeDeclaration type) {
      named = constructorOf(home.unit(), type, args.required("name"), rel);
    }
    if (!(named.decl() instanceof MethodDeclaration target)) {
      throw new Refusal(named.label() + " is not a method or constructor.");
    }
    IMethodBinding binding = target.resolveBinding();
    if (binding == null) {
      throw new Refusal(named.label() + " does not resolve; fix its compile errors first.");
    }
    if (binding.isVarargs() && !varargs) {
      throw new Refusal(named.label() + " has varargs, which these tools do not change yet.");
    }
    MethodFamily family = new MethodFamily(named.label());
    family.target = target;
    family.keepReferences = references;
    family.sources.putAll(sources);
    family.size = target.parameters().size();

    Set<String> keys =
        MethodFamily.family(
            project, sources.values().stream().map(Source::unit).toList(), binding, consequence);
    Map<String, Decl> declared = new HashMap<>();
    for (Source s : sources.values()) {
      s.unit()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration m) {
                  IMethodBinding b = m.resolveBinding();
                  if (b != null && keys.contains(MethodFamily.key(b))) {
                    declared.put(
                        MethodFamily.key(b),
                        new Decl(s, m, b, Signatures.open(s.text(), m.getName())));
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
    boolean functional =
        family.decls.stream()
            .anyMatch(d ->
                d.binding().getDeclaringClass().isInterface()
                    && Modifier.isAbstract(d.binding().getModifiers()));
    if (functional) {
      // A lambda implements an interface's method without naming it, so the files above can miss
      // it: look in every other file that has one.
      Holding lambdas =
          Holding.where(
              project,
              (f, t) ->
                  !sources.containsKey(f) && (t.contains("->") || references && t.contains("::")));
      for (Map.Entry<Path, CompilationUnit> e : lambdas.units().entrySet()) {
        Path f = e.getKey();
        Source s = new Source(f, project.relative(f), lambdas.texts().get(f), e.getValue());
        family.sources.put(f, s);
        family.findCalls(s, keys);
      }
    }
    return family;
  }

  /**
   * The method's own name: {@code Cart} for {@code Cart(String)}, {@code count} for {@code
   * Counter.count}.
   */
  static String simpleName(String name) {
    String head = name.contains("(") ? name.substring(0, name.indexOf('(')) : name;
    return Names.simpleName(head);
  }

  /** Adds the calls in {@code s} of the methods with {@code keys}; refuses method references. */
  private void findCalls(Source s, Set<String> keys) {
    String text = s.text();
    s.unit()
        .accept(
            new ASTVisitor() {
              boolean ours(IMethodBinding b) {
                return b != null && keys.contains(MethodFamily.key(b));
              }

              @Override
              public boolean visit(MethodInvocation n) {
                if (ours(n.resolveMethodBinding())) {
                  calls.add(new Call(s, n, n.arguments(), Signatures.open(text, n.getName()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(SuperMethodInvocation n) {
                if (ours(n.resolveMethodBinding())) {
                  calls.add(new Call(s, n, n.arguments(), Signatures.open(text, n.getName()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(ClassInstanceCreation n) {
                if (ours(Calls.binding(n))) {
                  calls.add(new Call(s, n, n.arguments(), Signatures.open(text, n.getType()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(ConstructorInvocation n) {
                if (ours(n.resolveConstructorBinding())) {
                  calls.add(
                      new Call(
                          s, n, n.arguments(), Signatures.after(text, n.getStartPosition()), -1));
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
                  calls.add(new Call(s, n, n.arguments(), Signatures.after(text, from), -1));
                }
                return true;
              }

              @Override
              public boolean visit(EnumConstantDeclaration n) {
                if (ours(Calls.binding(n))) {
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
                if (keepReferences) {
                  ITypeBinding type = n.resolveTypeBinding();
                  if (type != null && ours(type.getFunctionalInterfaceMethod())) {
                    references.add(new Reference(s, n, b));
                  }
                  return true;
                }
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
              public boolean visit(LambdaExpression n) {
                ITypeBinding type = n.resolveTypeBinding();
                if (type != null && ours(type.getFunctionalInterfaceMethod())) {
                  lambdas.add(new Lambda(s, n));
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
                        Names.methodLabel(d.binding())
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
                        Names.methodLabel(d.binding())
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

  /** The constructor of {@code type}, named {@code name} as a type: refused when it has none. */
  private static Declarations.Named constructorOf(
      CompilationUnit unit, AbstractTypeDeclaration type, String name, String rel) {
    String simple = type.getName().getIdentifier();
    boolean any = false;
    for (Object o : type.bodyDeclarations()) {
      any |= o instanceof MethodDeclaration m && m.isConstructor();
    }
    if (!any) {
      throw new Refusal(
          simple
              + " declares no constructor. Add one with add_declaration ("
              + (type instanceof EnumDeclaration ? "content: " + simple + "() {}" : "constructor")
              + "), then change it.");
    }
    return Declarations.find(unit, name + "." + simple, rel);
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
      for (MethodDeclaration m : Nodes.all(unit, MethodDeclaration.class)) {
        IMethodBinding b = m.resolveBinding();
        if (b != null) {
          String k = MethodFamily.key(b);
          declared.put(k, b);
          for (IMethodBinding over : Hierarchy.overridden(b)) {
            String o = MethodFamily.key(over);
            links.computeIfAbsent(k, x -> new HashSet<>()).add(o);
            links.computeIfAbsent(o, x -> new HashSet<>()).add(k);
            if (!Signatures.projects(project, over.getDeclaringClass())) {
              library.put(k, over);
            }
          }
        }
      }
    }
    Set<String> keys = new TreeSet<>();
    Deque<String> todo = new ArrayDeque<>(List.of(MethodFamily.key(binding)));
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
            Names.methodLabel(declared.get(k))
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

  /** The key that names {@code b}'s declaration in every file's bindings. */
  static String key(IMethodBinding b) {
    return b.getMethodDeclaration().getKey();
  }

  /**
   * The constructor a construction runs: {@code b} itself, or for an anonymous class ({@code new
   * T(...) {...}}, an enum constant with a body), whose own constructor {@code b} is, the
   * constructor of its superclass that the arguments go to: the one with {@code b}'s parameter
   * types, else the only one that takes as many. Null when there is none.
   */
  static IMethodBinding constructed(IMethodBinding b) {
    if (b == null || !b.getDeclaringClass().isAnonymous()) {
      return b;
    }
    ITypeBinding superclass = b.getDeclaringClass().getSuperclass();
    if (superclass == null) {
      return null;
    }
    List<IMethodBinding> sameCount = new ArrayList<>();
    for (IMethodBinding m : superclass.getDeclaredMethods()) {
      if (!m.isConstructor() || m.getParameterTypes().length != b.getParameterTypes().length) {
        continue;
      }
      sameCount.add(m);
      boolean same = true;
      for (int i = 0; i < m.getParameterTypes().length; i++) {
        same &=
            m.getParameterTypes()[i]
                .getErasure()
                .getKey()
                .equals(b.getParameterTypes()[i].getErasure().getKey());
      }
      if (same) {
        return m;
      }
    }
    return sameCount.size() == 1 ? sameCount.get(0) : null;
  }

  /** A declaration in the family; {@code open} is the offset just after its "(". */
  record Decl(Source file, MethodDeclaration m, IMethodBinding binding, int open) {}

  /**
   * A call of the family: its arguments, the offset just after its "(", or -1 for an enum constant
   * without one, then {@code bare} is where "(...)" goes.
   */
  record Call(Source file, ASTNode node, List<?> arguments, int open, int bare) {
    String where(ASTNode n) {
      return file.rel() + ", line " + file.line(n);
    }
  }

  /** A lambda that implements a family member: one more declaration whose parameters change. */
  record Lambda(Source file, LambdaExpression node) {
    /** Each parameter's text, as written: {@code a}, or {@code int a} when they are typed. */
    List<String> params() {
      List<String> out = new ArrayList<>();
      for (Object o : node.parameters()) {
        ASTNode p = (ASTNode) o;
        out.add(Nodes.source(file.text(), p));
      }
      return out;
    }

    /** Whether its parameters are written with their types. */
    boolean typed() {
      return !node.parameters().isEmpty()
          && node.parameters().get(0) instanceof SingleVariableDeclaration;
    }
  }

  /** A method reference that implements a family member, and the method it refers to. */
  record Reference(Source file, MethodReference node, IMethodBinding target) {}

  /** A file of the project, read and parsed with bindings. */
  record Source(Path file, String rel, String text, CompilationUnit unit) {
    int line(ASTNode node) {
      return unit.getLineNumber(node.getStartPosition());
    }
  }
}
