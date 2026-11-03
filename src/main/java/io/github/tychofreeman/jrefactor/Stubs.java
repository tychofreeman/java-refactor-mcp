package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * Stubs for what an edit uses and nothing declares: methods, constructors and classes of the
 * project, inferred from the use, with bodies that throw {@code UnsupportedOperationException("not
 * implemented")}, so a test written first compiles and fails.
 */
final class Stubs {

  private Stubs() {}

  /** What the running call declared, as its result says it. */
  private static final ThreadLocal<Map<String, List<String>>> DECLARED =
      ThreadLocal.withInitial(LinkedHashMap::new);

  /**
   * Declares stubs until the project has no compile errors beyond {@code before}, or none can be
   * declared. Writes each file it changes, first putting its old text (null for a new file) in
   * {@code old} when it is not there yet. Returns the errors left.
   */
  static List<String> declare(
      SourceProject project, Map<String, Integer> before, Map<Path, String> old)
      throws IOException {
    List<String> introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
    Round round = new Round(project, old);
    for (int i = 0; i < ROUNDS && !introduced.isEmpty(); i++) {
      round.wanted.clear();
      for (Path file : files(project, introduced)) {
        round.find(file);
      }
      if (round.wanted.isEmpty()) {
        break;
      }
      for (Map.Entry<Path, List<Stub>> target : round.wanted.entrySet()) {
        round.write(target.getKey(), target.getValue(), old);
      }
      introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
    }
    return introduced;
  }

  /** "; declared stubs a, b in f" for what the running call declared, or nothing; clears it. */
  static String said() {
    Map<String, List<String>> declared = DECLARED.get();
    DECLARED.remove();
    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, List<String>> file : declared.entrySet()) {
      parts.add(String.join(", ", file.getValue()) + " in " + file.getKey());
    }
    return parts.isEmpty() ? "" : "; declared stubs " + String.join("; ", parts);
  }

  /** Rounds of stubs, as members of a class become declarable once the class is. */
  private static final int ROUNDS = 5;

  private static final String BODY =
      " {\n  throw new UnsupportedOperationException(\"not implemented\");\n}";

  /**
   * One stub: {@code type} is the type it goes in ("Cart", "Outer.Inner"), or null for a new class,
   * whose {@code code} is then the whole file; {@code label} is what the result calls it, or null
   * for a member it does not name.
   */
  private record Stub(String type, String key, String label, String code) {}

  /** The project's files with errors in {@code introduced}, in order. */
  private static Set<Path> files(SourceProject project, List<String> introduced) {
    Set<Path> files = new LinkedHashSet<>();
    for (String error : introduced) {
      Path file = project.file(error.substring(0, error.indexOf(" | ")));
      if (file.toString().endsWith(".java") && Files.isRegularFile(file)) {
        files.add(file);
      }
    }
    return files;
  }

  /** The stubs of one call: those wanted in this round, by file, and what earlier ones made. */
  private static final class Round {
    final SourceProject project;
    final Map<Path, List<Stub>> wanted = new LinkedHashMap<>();
    final Set<String> done = new HashSet<>();
    final Set<String> created = new HashSet<>();
    final Set<String> uses = new TreeSet<>();

    /** The text of each file before the edit, to tell a removed member from a missing one. */
    final Map<Path, String> old;

    Round(SourceProject project, Map<Path, String> old) {
      this.project = project;
      this.old = old;
    }

    /**
     * Whether {@code type} in {@code file} had a method or constructor {@code name} before the
     * edit: one the edit removed, which is not declared again.
     */
    private boolean removed(Path file, String type, String name) {
      String before = old.get(file);
      if (before == null) {
        return false;
      }
      List<?> members = Jdt.parseSyntax(before).types();
      AbstractTypeDeclaration found = null;
      for (String part : type.split("\\.")) {
        found = null;
        for (Object o : members) {
          if (o instanceof AbstractTypeDeclaration t && t.getName().getIdentifier().equals(part)) {
            found = t;
          }
        }
        if (found == null) {
          return false;
        }
        members = found.bodyDeclarations();
      }
      for (Object o : members) {
        if (o instanceof MethodDeclaration m && m.getName().getIdentifier().equals(name)) {
          return true;
        }
      }
      return false;
    }

    /** Adds the stubs that the errors of {@code file} call for. */
    void find(Path file) throws IOException {
      CompilationUnit unit = Jdt.parse(project, file, Files.readString(file));
      List<IProblem> problems = new ArrayList<>(List.of(unit.getProblems()));
      problems.sort(Comparator.comparingInt(IProblem::getSourceStart));
      for (IProblem p : problems) {
        if (!p.isError()) {
          continue;
        }
        ASTNode node =
            NodeFinder.perform(unit, p.getSourceStart(), p.getSourceEnd() - p.getSourceStart() + 1);
        switch (p.getID()) {
          case IProblem.UndefinedMethod -> {
            MethodInvocation call = ancestor(node, MethodInvocation.class);
            if (call != null) {
              method(unit, call);
            }
          }
          case IProblem.UndefinedConstructor -> {
            ClassInstanceCreation creation = ancestor(node, ClassInstanceCreation.class);
            if (creation != null) {
              constructor(unit, creation);
            }
          }
          case IProblem.UndefinedName -> {
            // A class that does not exist yet, as the receiver of a static call: Tasks.run().
            if (node instanceof SimpleName n
                && n.getParent() instanceof MethodInvocation call
                && call.getExpression() == n) {
              type(file, n.getIdentifier());
            }
          }
          case IProblem.UndefinedType -> {
            if (p.getArguments().length > 0 && ancestor(node, ImportDeclaration.class) == null) {
              type(file, p.getArguments()[0]);
            }
          }
          default -> {}
        }
      }
    }

    private void method(CompilationUnit unit, MethodInvocation call) throws IOException {
      Expression receiver = call.getExpression();
      ITypeBinding owner;
      boolean isStatic;
      if (receiver == null) {
        AbstractTypeDeclaration enclosing = ancestor(call, AbstractTypeDeclaration.class);
        owner = enclosing == null ? null : enclosing.resolveBinding();
        isStatic = inStaticContext(call);
      } else if (receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding t) {
        owner = t;
        isStatic = true;
      } else {
        owner = receiver.resolveTypeBinding();
        isStatic = false;
      }
      Path target = sourceOf(owner);
      String name = call.getName().getIdentifier();
      if (target == null || owner.isInterface() || removed(target, label(owner), name)) {
        return;
      }
      owner = owner.getErasure();
      List<String> types = new ArrayList<>();
      String params = params(call.arguments(), types);
      String code =
          visibility(unit, owner)
              + (isStatic ? "static " : "")
              + resultType(call)
              + " "
              + name
              + "("
              + params
              + ")"
              + BODY;
      String label = label(owner) + "." + name + "(" + String.join(", ", types) + ")";
      want(target, new Stub(label(owner), label, label, code));
    }

    private void constructor(CompilationUnit unit, ClassInstanceCreation creation)
        throws IOException {
      ITypeBinding owner = creation.getType().resolveBinding();
      Path target = sourceOf(owner);
      if (target == null
          || owner.isInterface()
          || creation.getAnonymousClassDeclaration() != null) {
        return;
      }
      owner = owner.getErasure();
      String simple = owner.getName();
      String type = label(owner);
      if (removed(target, type, simple)) {
        return;
      }
      boolean implicit = !created.contains(owner.getQualifiedName());
      for (IMethodBinding m : owner.getDeclaredMethods()) {
        if (m.isConstructor() && !m.isDefaultConstructor()) {
          implicit = false;
        }
      }
      if (implicit) {
        // Declaring a constructor takes away the implicit one, which other code may use.
        want(target, new Stub(type, type + "()", null, visibility(unit, owner) + simple + "() {}"));
      }
      List<String> types = new ArrayList<>();
      String params = params(creation.arguments(), types);
      String label = type + "(" + String.join(", ", types) + ")";
      want(
          target,
          new Stub(
              type, label, label, visibility(unit, owner) + simple + "(" + params + ")" + BODY));
    }

    /** A class named {@code name} in the package of {@code file}, under the first source root. */
    private void type(Path file, String name) {
      String pkg = project.packageOf(file);
      if (pkg == null
          || name.isEmpty()
          || !Character.isUpperCase(name.charAt(0))
          || name.contains(".")) {
        return;
      }
      Path dir = project.sourceRoots().get(0);
      if (!pkg.isEmpty()) {
        dir = dir.resolve(pkg.replace('.', '/'));
      }
      Path target = dir.resolve(name + ".java");
      if (Files.exists(target)) {
        return;
      }
      created.add(pkg.isEmpty() ? name : pkg + "." + name);
      String header = pkg.isEmpty() ? "" : "package " + pkg + ";\n\n";
      want(
          target,
          new Stub(
              null, "class " + name, "class " + name, header + "public class " + name + " {\n}\n"));
    }

    private void want(Path target, Stub stub) {
      if (done.add(project.relative(target) + "\0" + stub.key())) {
        wanted.computeIfAbsent(target, k -> new ArrayList<>()).add(stub);
      }
    }

    /** Writes {@code stubs} into {@code file}, keeping its old text in {@code old}. */
    void write(Path file, List<Stub> stubs, Map<Path, String> old) throws IOException {
      String before = Files.exists(file) ? Files.readString(file) : null;
      old.putIfAbsent(file, before);
      String rel = project.relative(file);
      String text = before;
      Map<String, List<String>> byType = new LinkedHashMap<>();
      List<String> labels = DECLARED.get().computeIfAbsent(rel, k -> new ArrayList<>());
      for (Stub stub : stubs) {
        if (stub.type() == null) {
          text = stub.code();
        } else {
          byType.computeIfAbsent(stub.type(), k -> new ArrayList<>()).add(stub.code());
        }
        if (stub.label() != null) {
          labels.add(stub.label());
        }
      }
      if (text == null) {
        return;
      }
      for (Map.Entry<String, List<String>> type : byType.entrySet()) {
        String content = String.join("\n\n", type.getValue());
        text = Declarations.plan(text, rel, type.getKey(), content, null, null).text();
      }
      if (before != null) {
        text = Imports.fix(project, file, before, text, List.copyOf(uses)).text();
      }
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
    }

    /** The project file that declares {@code type}, or null when it is not the project's. */
    private Path sourceOf(ITypeBinding type) {
      if (type == null
          || type.isRecovered()
          || type.isAnonymous()
          || type.isLocal()
          || type.isArray()
          || type.isPrimitive()) {
        return null;
      }
      ITypeBinding top = type.getErasure();
      while (top.getDeclaringClass() != null) {
        top = top.getDeclaringClass();
      }
      String path = top.getQualifiedName().replace('.', '/') + ".java";
      for (Path root : project.sourceRoots()) {
        if (Files.isRegularFile(root.resolve(path))) {
          return root.resolve(path);
        }
      }
      return null;
    }

    /** "Type name, Type name" for {@code arguments}, adding each type to {@code types}. */
    private String params(List<?> arguments, List<String> types) {
      List<String> params = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (Object o : arguments) {
        Expression argument = (Expression) o;
        String type = typeName(argument.resolveTypeBinding());
        types.add(type);
        String name = paramName(argument, type);
        String unique = name;
        for (int n = 2; !names.add(unique); n++) {
          unique = name + n;
        }
        params.add(type + " " + unique);
      }
      return String.join(", ", params);
    }

    /**
     * The type a call's result has where it is used: void as a statement, boolean as a condition,
     * else that of what it is declared, assigned, returned, compared or passed as; else Object.
     */
    private String resultType(Expression use) {
      ASTNode child = use;
      ASTNode parent = use.getParent();
      while (parent instanceof ParenthesizedExpression) {
        child = parent;
        parent = parent.getParent();
      }
      if (parent instanceof ExpressionStatement) {
        return "void";
      }
      if (parent instanceof LambdaExpression lambda && lambda.getBody() == child) {
        return lambdaResult(lambda);
      }
      if (parent instanceof VariableDeclarationFragment f && f.getInitializer() == child) {
        Type type =
            switch (f.getParent()) {
              case VariableDeclarationStatement s -> s.getType();
              case VariableDeclarationExpression e -> e.getType();
              case FieldDeclaration d -> d.getType();
              default -> null;
            };
        return type == null || type.isVar() ? "Object" : typeName(type.resolveBinding());
      }
      if (parent instanceof Assignment a && a.getRightHandSide() == child) {
        return typeName(a.getLeftHandSide().resolveTypeBinding());
      }
      if (parent instanceof ReturnStatement) {
        ASTNode in = parent;
        while (in != null
            && !(in instanceof MethodDeclaration)
            && !(in instanceof LambdaExpression)) {
          in = in.getParent();
        }
        if (in instanceof MethodDeclaration m && m.getReturnType2() != null) {
          return typeName(m.getReturnType2().resolveBinding());
        }
        return in instanceof LambdaExpression lambda ? lambdaResult(lambda) : "Object";
      }
      if (parent instanceof IfStatement
          || parent instanceof WhileStatement
          || parent instanceof DoStatement
          || (parent instanceof ConditionalExpression c && c.getExpression() == child)
          || (parent instanceof PrefixExpression p
              && p.getOperator() == PrefixExpression.Operator.NOT)) {
        return "boolean";
      }
      if (parent instanceof InfixExpression in) {
        if (in.getOperator() == InfixExpression.Operator.CONDITIONAL_AND
            || in.getOperator() == InfixExpression.Operator.CONDITIONAL_OR) {
          return "boolean";
        }
        Expression other =
            in.getLeftOperand() == child ? in.getRightOperand() : in.getLeftOperand();
        return typeName(other.resolveTypeBinding());
      }
      if (parent instanceof MethodInvocation call && call.arguments().contains(child)) {
        String name = call.getName().getIdentifier();
        if (name.equals("assertTrue") || name.equals("assertFalse")) {
          return "boolean";
        }
        List<?> args = call.arguments();
        int at = args.indexOf(child);
        if (name.startsWith("assert")) {
          int first = args.size() == 3 && args.get(0) instanceof StringLiteral ? 1 : 0;
          for (int i = first; i < Math.min(first + 2, args.size()); i++) {
            ITypeBinding type = ((Expression) args.get(i)).resolveTypeBinding();
            if (i != at && type != null && !type.isNullType() && !type.isRecovered()) {
              return typeName(type);
            }
          }
        }
        IMethodBinding m = call.resolveMethodBinding();
        if (m != null && at < m.getParameterTypes().length) {
          return typeName(m.getParameterTypes()[at]);
        }
      }
      return "Object";
    }

    /** The result type of the method {@code lambda} implements, or Object when unknown. */
    private String lambdaResult(LambdaExpression lambda) {
      IMethodBinding m = lambda.resolveMethodBinding();
      return m == null ? "Object" : typeName(m.getReturnType());
    }

    /** How the stub writes {@code b}, noting the classes it names for their imports. */
    private String typeName(ITypeBinding b) {
      if (b == null || b.isNullType() || b.isRecovered()) {
        return "Object";
      }
      if (b.isCapture() || b.isWildcardType() || b.isTypeVariable()) {
        return typeName(b.getErasure());
      }
      if (b.isAnonymous()) {
        return typeName(b.getInterfaces().length > 0 ? b.getInterfaces()[0] : b.getSuperclass());
      }
      if (b.isPrimitive()) {
        return b.getName();
      }
      if (b.isArray()) {
        return typeName(b.getElementType()) + "[]".repeat(b.getDimensions());
      }
      ITypeBinding top = b.getErasure();
      String name = label(top);
      while (top.getDeclaringClass() != null) {
        top = top.getDeclaringClass();
      }
      if (!top.getPackage().isUnnamed() && !top.getPackage().getName().equals("java.lang")) {
        uses.add(top.getQualifiedName());
      }
      List<String> arguments = new ArrayList<>();
      for (ITypeBinding argument : b.getTypeArguments()) {
        arguments.add(typeName(argument));
      }
      return arguments.isEmpty() ? name : name + "<" + String.join(", ", arguments) + ">";
    }
  }

  /** "Outer.Inner" for a nested type, the simple name for a top-level one. */
  private static String label(ITypeBinding type) {
    ITypeBinding t = type.getErasure();
    return t.getDeclaringClass() == null
        ? t.getName()
        : label(t.getDeclaringClass()) + "." + t.getName();
  }

  /** "public " when {@code owner} is in another package than {@code unit}, else nothing. */
  private static String visibility(CompilationUnit unit, ITypeBinding owner) {
    String here =
        unit.getPackage() == null ? "" : unit.getPackage().getName().getFullyQualifiedName();
    return owner.getPackage().getName().equals(here) ? "" : "public ";
  }

  /** A parameter's name: the argument's, when it is a variable or a getter, else from its type. */
  private static String paramName(Expression argument, String type) {
    String name =
        switch (argument) {
          case SimpleName n -> n.getIdentifier();
          case QualifiedName n -> n.getName().getIdentifier();
          case FieldAccess f -> f.getName().getIdentifier();
          case MethodInvocation m when m.arguments().isEmpty() ->
              getter(m.getName().getIdentifier());
          default -> null;
        };
    if (name == null) {
      String simple = type.replaceAll("<.*", "").replace("[]", "s");
      simple = simple.substring(simple.lastIndexOf('.') + 1);
      name =
          Character.isLowerCase(simple.charAt(0))
              ? simple.substring(0, 1)
              : Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
    return SourceVersion.isName(name) ? name : "value";
  }

  /** "name" for getName, isName or name. */
  private static String getter(String method) {
    for (String prefix : List.of("get", "is")) {
      if (method.length() > prefix.length()
          && method.startsWith(prefix)
          && Character.isUpperCase(method.charAt(prefix.length()))) {
        String rest = method.substring(prefix.length());
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
      }
    }
    return method;
  }

  private static boolean inStaticContext(ASTNode node) {
    for (ASTNode n = node.getParent(); n != null; n = n.getParent()) {
      if (n instanceof AnonymousClassDeclaration) {
        return false;
      }
      if (n instanceof BodyDeclaration d) {
        return Modifier.isStatic(d.getModifiers());
      }
    }
    return false;
  }

  /** {@code node} or its nearest ancestor of {@code type}, or null. */
  private static <T> T ancestor(ASTNode node, Class<T> type) {
    for (ASTNode n = node; n != null; n = n.getParent()) {
      if (type.isInstance(n)) {
        return type.cast(n);
      }
    }
    return null;
  }
}
