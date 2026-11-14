package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  final List<Signatures.Decl> decls = new ArrayList<>();
  final List<Signatures.Call> calls = new ArrayList<>();

  /** The lambdas that implement a member of the family. */
  final List<Signatures.Lambda> lambdas = new ArrayList<>();

  int size;
  MethodDeclaration target;

  /** The files parsed, by path: where a declaration found later is edited. */
  final Map<Path, Signatures.Source> sources = new LinkedHashMap<>();

  /** With {@link #keepReferences}, the method references that implement a member of the family. */
  final List<Signatures.Reference> references = new ArrayList<>();

  /** Whether a method reference is kept in {@link #references}, not refused. */
  private boolean keepReferences;

  private MethodFamily(String label) {
    this.label = label;
  }

  static MethodFamily of(SourceProject project, Args args) throws Exception {
    return of(project, args, false, "so its parameters cannot change.");
  }

  /**
   * The family of the method {@code args} name. With {@code references}, a method reference is not
   * refused: one that implements the family is kept. {@code consequence} ends the refusal of a
   * method that overrides a library's.
   */
  static MethodFamily of(SourceProject project, Args args, boolean references, String consequence)
      throws Exception {
    String rel = args.required("file");
    Path path = project.javaFile(rel);
    Map<Path, String> texts = new LinkedHashMap<>();
    for (Path f : project.javaFiles()) {
      texts.put(f, Files.readString(f));
    }
    if (!texts.containsKey(path)) {
      throw new Refusal(rel + " is not a Java file under a source root.");
    }
    // A family member and a call of one have the method's simple name, so a file without that
    // name cannot hold either: only the files with it are parsed.
    String simple = simpleName(args.required("name"));
    List<Path> files = new ArrayList<>();
    for (Map.Entry<Path, String> e : texts.entrySet()) {
      if (e.getKey().equals(path) || e.getValue().contains(simple)) {
        files.add(e.getKey());
      }
    }
    Map<Path, Signatures.Source> sources = new LinkedHashMap<>();
    Map<Path, CompilationUnit> units = Jdt.parseAll(project, files);
    for (Path f : files) {
      sources.put(f, new Signatures.Source(f, project.relative(f), texts.get(f), units.get(f)));
    }
    Signatures.Source home = sources.get(path);
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
    if (binding.isVarargs()) {
      throw new Refusal(named.label() + " has varargs, which these tools do not change yet.");
    }
    MethodFamily family = new MethodFamily(named.label());
    family.target = target;
    family.keepReferences = references;
    family.sources.putAll(sources);
    family.size = target.parameters().size();

    Set<String> keys =
        Signatures.family(
            project,
            sources.values().stream().map(Signatures.Source::unit).toList(),
            binding,
            consequence);
    Map<String, Signatures.Decl> declared = new HashMap<>();
    for (Signatures.Source s : sources.values()) {
      s.unit()
          .accept(
              new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration m) {
                  IMethodBinding b = m.resolveBinding();
                  if (b != null && keys.contains(Signatures.key(b))) {
                    declared.put(
                        Signatures.key(b),
                        new Signatures.Decl(s, m, b, Signatures.open(s.text(), m.getName())));
                  }
                  return true;
                }
              });
    }
    for (String k : keys) {
      Signatures.Decl d = declared.get(k);
      if (d != null) {
        family.decls.add(d);
      }
    }
    for (Signatures.Source s : sources.values()) {
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
      List<Path> lambdas = new ArrayList<>();
      for (Map.Entry<Path, String> e : texts.entrySet()) {
        if (!sources.containsKey(e.getKey())
            && (e.getValue().contains("->") || references && e.getValue().contains("::"))) {
          lambdas.add(e.getKey());
        }
      }
      Map<Path, CompilationUnit> more = Jdt.parseAll(project, lambdas);
      for (Path f : lambdas) {
        Signatures.Source s =
            new Signatures.Source(f, project.relative(f), texts.get(f), more.get(f));
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
    return head.substring(head.lastIndexOf('.') + 1);
  }

  /** Adds the calls in {@code s} of the methods with {@code keys}; refuses method references. */
  private void findCalls(Signatures.Source s, Set<String> keys) {
    String text = s.text();
    s.unit()
        .accept(
            new ASTVisitor() {
              boolean ours(IMethodBinding b) {
                return b != null && keys.contains(Signatures.key(b));
              }

              @Override
              public boolean visit(MethodInvocation n) {
                if (ours(n.resolveMethodBinding())) {
                  calls.add(
                      new Signatures.Call(
                          s, n, n.arguments(), Signatures.open(text, n.getName()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(SuperMethodInvocation n) {
                if (ours(n.resolveMethodBinding())) {
                  calls.add(
                      new Signatures.Call(
                          s, n, n.arguments(), Signatures.open(text, n.getName()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(ClassInstanceCreation n) {
                if (ours(Calls.binding(n))) {
                  calls.add(
                      new Signatures.Call(
                          s, n, n.arguments(), Signatures.open(text, n.getType()), -1));
                }
                return true;
              }

              @Override
              public boolean visit(ConstructorInvocation n) {
                if (ours(n.resolveConstructorBinding())) {
                  calls.add(
                      new Signatures.Call(
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
                  calls.add(
                      new Signatures.Call(s, n, n.arguments(), Signatures.after(text, from), -1));
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
                  calls.add(new Signatures.Call(s, n, n.arguments(), parens ? i + 1 : -1, end));
                }
                return true;
              }

              private boolean reference(MethodReference n, IMethodBinding b) {
                if (keepReferences) {
                  ITypeBinding type = n.resolveTypeBinding();
                  if (type != null && ours(type.getFunctionalInterfaceMethod())) {
                    references.add(new Signatures.Reference(s, n, b));
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
                  lambdas.add(new Signatures.Lambda(s, n));
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
    for (Signatures.Decl d : decls) {
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
                        Signatures.label(d.binding())
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
    for (Signatures.Decl d : decls) {
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
                        Signatures.label(d.binding())
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
}
