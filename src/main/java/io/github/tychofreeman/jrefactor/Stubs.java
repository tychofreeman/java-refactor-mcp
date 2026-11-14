package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.lang.model.SourceVersion;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
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
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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

  /** The stubs one change declared: the labels the result names, by the file they went in. */
  record Declared(Map<String, List<String>> byFile) {

    static final Declared NONE = new Declared(Map.of());

    /** "; declared stubs a, b in f" for these stubs, or nothing. */
    String text() {
      List<String> parts = new ArrayList<>();
      for (Map.Entry<String, List<String>> file : byFile.entrySet()) {
        parts.add(String.join(", ", file.getValue()) + " in " + file.getKey());
      }
      return parts.isEmpty() ? "" : "; declared stubs " + String.join("; ", parts);
    }
  }

  /** The compile errors a change left beyond the ones before it, and the stubs it declared. */
  record Check(List<TypeCheck.Added> introduced, Declared declared) {}

  /**
   * Declares stubs until the project has no compile errors beyond {@code before}, or none can be
   * declared. Writes each file it changes, first putting its old text (null for a new file) in
   * {@code old} when it is not there yet. Returns the errors left.
   */
  static Check declare(
      SourceProject project, Map<TypeCheck.Problem, Integer> before, Map<Path, String> old)
      throws IOException {
    List<TypeCheck.Added> introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
    if (!project.session().editing) {
      return new Check(introduced, Declared.NONE);
    }
    Round round = new Round(project, old);
    for (int i = 0; i < ROUNDS && !introduced.isEmpty(); i++) {
      round.wanted.clear();
      round.wantedUses.clear();
      boolean moved = false;
      for (Path file : files(project, introduced)) {
        moved |= FieldOrder.fix(project, file, old);
        round.find(file);
      }
      if (round.wanted.isEmpty() && !moved) {
        break;
      }
      for (Map.Entry<Path, List<Stub>> target : round.wanted.entrySet()) {
        round.write(target.getKey(), target.getValue(), old);
      }
      introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
    }
    return new Check(introduced, new Declared(round.declared));
  }

  /** Rounds of stubs, as members of a class become declarable once the class is. */
  private static final int ROUNDS = 5;

  private static final String BODY =
      " {\n  throw new UnsupportedOperationException(\"not implemented\");\n}";

  /** What ends a field stub's line, by which add_declaration knows it. */
  static final String FIELD_MARK = "// not implemented";

  /** Names of test doubles and helpers, whose stub classes stay in the test's root. */
  private static final Pattern TEST_DOUBLE =
      Pattern.compile(
          "(Fake|Stub|Mock|Dummy|Spy)[A-Z].*|.*(Test|Tests|Fixture|Helper|Fake|Stub|Mock)");

  /**
   * One stub: {@code type} is the type it goes in ("Cart", "Outer.Inner"), or null for a new class,
   * whose {@code code} is then the whole file; {@code label} is what the result calls it, or null
   * for a member it does not name.
   */
  private record Stub(String type, String key, String label, String code) {}

  /** A constructor that assigns {@code fields} from its parameters, and its code. */
  private record Setting(String code, List<String> fields) {}

  /** The project's files with errors in {@code introduced}, in order. */
  private static Set<Path> files(SourceProject project, List<TypeCheck.Added> introduced) {
    Set<Path> files = new LinkedHashSet<>();
    for (TypeCheck.Added error : introduced) {
      Path file = project.file(error.problem().rel());
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

    /** The names the stub being built uses, which its imports are picked from. */
    final Set<String> uses = new TreeSet<>();

    /** The type variables a stub being written can name: those of the type it goes in. */
    private Set<String> inScope = Set.of();

    /** The names the stubs wanted in each file use: the picks for that file's imports. */
    final Map<Path, Set<String>> wantedUses = new HashMap<>();

    /** The labels of the stubs written, by the file they went in. */
    final Map<String, List<String>> declared = new LinkedHashMap<>();

    /** The text of each file before the edit, to tell a removed member from a missing one. */
    final Map<Path, String> old;

    Round(SourceProject project, Map<Path, String> old) {
      this.project = project;
      this.old = old;
    }

    /**
     * Whether {@code type} in {@code file} had a method or constructor {@code name} (a field, with
     * {@code field}) before the edit: one the edit removed, which is not declared again.
     */
    private boolean removed(Path file, String type, String name, boolean field) {
      String before = old.get(file);
      if (before == null) {
        return false;
      }
      AbstractTypeDeclaration found =
          Declarations.typePath(Jdt.parseSyntax(before), type.split("\\."));
      if (found == null) {
        return false;
      }
      List<?> members = found.bodyDeclarations();
      for (Object o : members) {
        if (!field
            && o instanceof MethodDeclaration m
            && m.getName().getIdentifier().equals(name)) {
          return true;
        }
        if (field && o instanceof FieldDeclaration f) {
          for (Object v : f.fragments()) {
            if (((VariableDeclarationFragment) v).getName().getIdentifier().equals(name)) {
              return true;
            }
          }
        }
      }
      return false;
    }

    /**
     * Whether an edit removed {@code name} from {@code owner} or, for an unqualified call, from a
     * type around the call: the call may have meant that method.
     */
    private boolean removedAround(
        MethodInvocation call, ITypeBinding owner, Path target, String name) {
      if (removed(target, Names.typeLabel(owner), name, false)
          || removedInSupertypes(owner, name)) {
        return true;
      }
      if (call.getExpression() != null) {
        return false;
      }
      for (ASTNode a = call.getParent(); a != null; a = a.getParent()) {
        if (a instanceof AbstractTypeDeclaration t
            && t.resolveBinding() != null
            && (removed(target, Names.typeLabel(t.resolveBinding()), name, false)
                || removedInSupertypes(t.resolveBinding(), name))) {
          return true;
        }
      }
      return false;
    }

    /**
     * Whether an edit removed {@code name} from a supertype of {@code type}. Each supertype is read
     * in its own file, since a call inside a subclass has no file of the supertype's.
     */
    private boolean removedInSupertypes(ITypeBinding type, String name) {
      for (ITypeBinding s : Hierarchy.supertypes(type, false)) {
        Path file = project.fileOf(s);
        if (file != null && removed(file, Names.typeLabel(s.getErasure()), name, false)) {
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
          // A call that fits no overload of a project method: the stub is an overload, typed from
          // the call.
          case IProblem.UndefinedMethod, IProblem.ParameterMismatch -> {
            MethodInvocation call = Nodes.ancestorOrSelf(node, MethodInvocation.class);
            if (call != null) {
              method(unit, call);
            }
          }
          case IProblem.UndefinedConstructor -> {
            ClassInstanceCreation creation =
                Nodes.ancestorOrSelf(node, ClassInstanceCreation.class);
            if (creation != null) {
              constructor(unit, creation);
            }
          }
          case IProblem.UndefinedName, IProblem.UnresolvedVariable -> {
            // A class that does not exist yet, as the receiver of a static call: Tasks.run().
            if (node instanceof SimpleName n
                && n.getParent() instanceof MethodInvocation call
                && call.getExpression() == n) {
              type(file, n.getIdentifier());
            }
            // A class that does not exist yet, as the qualifier of a constant: Limits.MAX.
            if (node instanceof SimpleName n
                && n.getParent() instanceof QualifiedName q
                && q.getQualifier() == n
                && Character.isUpperCase(n.getIdentifier().charAt(0))) {
              type(file, n.getIdentifier());
            }
            // A field that does not exist yet, read or written by its bare name: return borrowed.
            if (node instanceof SimpleName n
                && Character.isLowerCase(n.getIdentifier().charAt(0))
                && !(n.getParent() instanceof MethodInvocation call && call.getExpression() == n)
                && !(n.getParent() instanceof QualifiedName)) {
              bareField(unit, n);
            }
          }
          // A static field of a project class that nothing declares: Limits.MAX.
          case IProblem.UndefinedField -> {
            if (node instanceof SimpleName n
                && n.getParent() instanceof QualifiedName q
                && q.getName() == n
                && q.getQualifier().resolveBinding() instanceof ITypeBinding) {
              field(unit, q);
            }
          }
          case IProblem.UndefinedType -> {
            if (p.getArguments().length > 0
                && Nodes.ancestorOrSelf(node, ImportDeclaration.class) == null) {
              type(file, p.getArguments()[0]);
            }
          }
          default -> {}
        }
      }
    }

    private void method(CompilationUnit unit, MethodInvocation call) throws IOException {
      uses.clear();
      Expression receiver = call.getExpression();
      ITypeBinding owner;
      boolean isStatic;
      if (receiver == null) {
        AbstractTypeDeclaration enclosing =
            Nodes.ancestorOrSelf(call, AbstractTypeDeclaration.class);
        owner = enclosing == null ? null : enclosing.resolveBinding();
        isStatic = inStaticContext(call);
      } else if (receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding t) {
        owner = t;
        isStatic = true;
      } else {
        owner = receiver.resolveTypeBinding();
        isStatic = false;
      }
      Path target = project.fileOf(owner);
      String name = call.getName().getIdentifier();
      if (target == null || owner.isInterface() || removedAround(call, owner, target, name)) {
        return;
      }
      inScope = typeVariablesOf(owner);
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
      String label = Names.typeLabel(owner) + "." + name + "(" + String.join(", ", types) + ")";
      want(target, new Stub(Names.typeLabel(owner), label, label, code));
    }

    /**
     * A static field {@code read} names in a project class, typed from the use, with no value: a
     * stub that {@code add_declaration} of the real field replaces, known by its comment.
     */
    private void field(CompilationUnit unit, QualifiedName read) throws IOException {
      uses.clear();
      ITypeBinding owner = (ITypeBinding) read.getQualifier().resolveBinding();
      Path target = project.fileOf(owner);
      if (target == null || owner.isInterface()) {
        return;
      }
      inScope = Set.of();
      owner = owner.getErasure();
      String name = read.getName().getIdentifier();
      String code =
          visibility(unit, owner) + "static " + resultType(read) + " " + name + "; " + FIELD_MARK;
      String label = Names.typeLabel(owner) + "." + name;
      want(target, new Stub(Names.typeLabel(owner), label, label, code));
    }

    private void constructor(CompilationUnit unit, ClassInstanceCreation creation)
        throws IOException {
      uses.clear();
      ITypeBinding owner = creation.getType().resolveBinding();
      Path target = project.fileOf(owner);
      if (target == null
          || owner.isInterface()
          || creation.getAnonymousClassDeclaration() != null) {
        return;
      }
      inScope = typeVariablesOf(owner);
      owner = owner.getErasure();
      String simple = owner.getName();
      String type = Names.typeLabel(owner);
      if (removed(target, type, simple, false)) {
        return;
      }
      boolean needsDefault = !created.contains(owner.getQualifiedName());
      for (IMethodBinding m : owner.getDeclaredMethods()) {
        if (m.isConstructor() && !m.isDefaultConstructor()) {
          needsDefault = false;
        }
      }
      if (needsDefault) {
        // Declaring a constructor takes away the implicit one, which other code may use.
        want(target, new Stub(type, type + "()", null, visibility(unit, owner) + simple + "() {}"));
      }
      List<String> types = new ArrayList<>();
      String params = params(creation.arguments(), types);
      String label = type + "(" + String.join(", ", types) + ")";
      Setting setting =
          setting(target, owner, creation.arguments(), visibility(unit, owner) + simple);
      if (setting != null) {
        want(
            target,
            new Stub(
                type,
                label,
                label + " setting " + String.join(", ", setting.fields()),
                setting.code()));
        return;
      }
      want(
          target,
          new Stub(
              type, label, label, visibility(unit, owner) + simple + "(" + params + ")" + BODY));
    }

    /**
     * A constructor of {@code owner}, declared in {@code target}, headed {@code head} (its access
     * and name), that assigns each of {@code arguments} to the one field it fits: declared in
     * {@code owner} with the argument's type, not static, not final with a value, and, of several,
     * the one the argument names. Null when an argument fits none or several, two fit one field, or
     * there are no arguments.
     */
    private Setting setting(Path target, ITypeBinding owner, List<?> arguments, String head)
        throws IOException {
      if (arguments.isEmpty()) {
        return null;
      }
      Map<String, String> written = new LinkedHashMap<>();
      CompilationUnit source = Edits.parses(Files.readString(target), project.relative(target));
      for (TypeDeclaration t : Nodes.all(source, TypeDeclaration.class)) {
        if (!t.getName().getIdentifier().equals(owner.getName())) {
          continue;
        }
        for (FieldDeclaration f : t.getFields()) {
          for (Object o : f.fragments()) {
            VariableDeclarationFragment v = (VariableDeclarationFragment) o;
            if (!Modifier.isStatic(f.getModifiers())
                && !(Modifier.isFinal(f.getModifiers()) && v.getInitializer() != null)) {
              written.put(
                  v.getName().getIdentifier(), f.getType() + "[]".repeat(v.getExtraDimensions()));
            }
          }
        }
      }
      Map<String, ITypeBinding> bound = new HashMap<>();
      for (IVariableBinding f : owner.getDeclaredFields()) {
        if (written.containsKey(f.getName())) {
          bound.put(f.getName(), f.getType());
        }
      }
      List<String> fields = new ArrayList<>();
      for (Object a : arguments) {
        ITypeBinding type = ((Expression) a).resolveTypeBinding();
        if (type == null) {
          return null;
        }
        List<String> fit = new ArrayList<>();
        for (String field : written.keySet()) {
          if (bound.containsKey(field) && bound.get(field).isEqualTo(type)) {
            fit.add(field);
          }
        }
        if (fit.size() > 1 && a instanceof SimpleName n && fit.contains(n.getIdentifier())) {
          fit = List.of(n.getIdentifier());
        }
        if (fit.size() != 1 || fields.contains(fit.get(0))) {
          return null;
        }
        fields.add(fit.get(0));
      }
      List<String> params = new ArrayList<>();
      List<String> body = new ArrayList<>();
      for (String field : fields) {
        params.add(written.get(field) + " " + field);
        body.add("  this." + field + " = " + field + ";");
      }
      return new Setting(
          head + "(" + String.join(", ", params) + ") {\n" + String.join("\n", body) + "\n}",
          fields);
    }

    /**
     * A class named {@code name} in the package of {@code file}, under the main (first) source
     * root; a name that reads as a test double or helper, under the root that holds {@code file}.
     */
    private void type(Path file, String name) {
      String pkg = project.packageOf(file);
      if (pkg == null
          || name.isEmpty()
          || !Character.isUpperCase(name.charAt(0))
          || name.contains(".")) {
        return;
      }
      // The main root (the first), in the using file's package; a test double or helper a test
      // uses stays in the test's own root.
      Path dir = project.sourceRoots().get(0);
      if (TEST_DOUBLE.matcher(name).matches()) {
        for (Path sourceRoot : project.sourceRoots()) {
          if (file.startsWith(sourceRoot)) {
            dir = sourceRoot;
            break;
          }
        }
      }
      if (!pkg.isEmpty()) {
        dir = dir.resolve(pkg.replace('.', '/'));
      }
      Path target = dir.resolve(name + ".java");
      // A file the edit deleted held the class: it was removed, not missing.
      if (Files.exists(target) || old.get(target) != null) {
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
        wantedUses.computeIfAbsent(target, k -> new TreeSet<>()).addAll(uses);
      }
    }

    /** Writes {@code stubs} into {@code file}, keeping its old text in {@code old}. */
    void write(Path file, List<Stub> stubs, Map<Path, String> old) throws IOException {
      String before = Files.exists(file) ? Files.readString(file) : null;
      if (!old.containsKey(file)) {
        old.put(file, before); // putIfAbsent would replace a null: a file that did not exist
      }
      String rel = project.relative(file);
      String text = before;
      Map<String, List<String>> byType = new LinkedHashMap<>();
      List<String> labels = declared.computeIfAbsent(rel, k -> new ArrayList<>());
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
        text = Declarations.plan(text, rel, type.getKey(), content).text();
      }
      if (before != null) {
        text =
            Imports.fix(
                    project,
                    file,
                    before,
                    text,
                    List.copyOf(wantedUses.getOrDefault(file, Set.of())))
                .text();
      }
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
    }

    /**
     * A functional interface that a lambda with as many parameters as {@code lambda} has can be
     * passed as, with Object for each type. Object for more than two.
     */
    private String lambdaType(LambdaExpression lambda) {
      return switch (lambda.parameters().size()) {
        case 0 -> functional("Supplier", "Object");
        case 1 -> functional("Function", "Object, Object");
        case 2 -> functional("BiFunction", "Object, Object, Object");
        default -> "Object";
      };
    }

    private String functional(String name, String arguments) {
      uses.add("java.util.function." + name);
      return name + "<" + arguments + ">";
    }

    /** "Type name, Type name" for {@code arguments}, adding each type to {@code types}. */
    private String params(List<?> arguments, List<String> types) {
      List<String> params = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (Object o : arguments) {
        Expression argument = (Expression) o;
        String type =
            argument instanceof LambdaExpression lambda
                ? lambdaType(lambda)
                : typeName(argument.resolveTypeBinding());
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
      if (parent instanceof EnhancedForStatement f && f.getExpression() == child) {
        uses.add("java.util.List");
        ITypeBinding element = f.getParameter().getType().resolveBinding();
        return "List<"
            + (element != null && element.isPrimitive() ? wrapper(element) : typeName(element))
            + ">";
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
      if (parent instanceof Assignment a && a.getLeftHandSide() == child) {
        return typeName(a.getRightHandSide().resolveTypeBinding());
      }
      if (parent instanceof PostfixExpression
          || (parent instanceof PrefixExpression p
              && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                  || p.getOperator() == PrefixExpression.Operator.DECREMENT))) {
        return "int";
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
      if (b.isTypeVariable() && inScope.contains(b.getName())) {
        return b.getName();
      }
      if (b.isCapture() && b.getWildcard() != null) {
        return typeName(b.getWildcard());
      }
      if (b.isWildcardType()) {
        ITypeBinding bound = b.getBound();
        return bound == null
            ? "?"
            : (b.isUpperbound() ? "? extends " : "? super ") + typeName(bound);
      }
      if (b.isCapture() || b.isTypeVariable()) {
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
      String name = Names.typeLabel(top);
      top = Names.outermost(top);
      if (!top.getPackage().isUnnamed() && !top.getPackage().getName().equals("java.lang")) {
        uses.add(top.getQualifiedName());
      }
      List<String> arguments = new ArrayList<>();
      for (ITypeBinding argument : b.getTypeArguments()) {
        arguments.add(typeName(argument));
      }
      return arguments.isEmpty() ? name : name + "<" + String.join(", ", arguments) + ">";
    }

    /**
     * A field {@code use} names bare in the named type around it, typed from the use, static in
     * static code: a stub that {@code add_declaration} of the real field replaces, as {@link
     * #field}'s.
     */
    private void bareField(CompilationUnit unit, SimpleName use) throws IOException {
      uses.clear();
      AbstractTypeDeclaration enclosing = Nodes.ancestorOrSelf(use, AbstractTypeDeclaration.class);
      ITypeBinding owner = enclosing == null ? null : enclosing.resolveBinding();
      Path target = project.fileOf(owner);
      String name = use.getIdentifier();
      if (target == null
          || owner.isInterface()
          || removed(target, Names.typeLabel(owner), name, true)) {
        return;
      }
      inScope = inStaticContext(use) ? Set.of() : typeVariablesOf(owner);
      owner = owner.getErasure();
      String code =
          (inStaticContext(use) ? "static " : "")
              + resultType(use)
              + " "
              + name
              + "; "
              + FIELD_MARK;
      String label = Names.typeLabel(owner) + "." + name;
      want(target, new Stub(Names.typeLabel(owner), label, label, code));
    }
  }

  /** The class that boxes the primitive {@code b}, as a type argument needs it. */
  private static String wrapper(ITypeBinding b) {
    return switch (b.getName()) {
      case "int" -> "Integer";
      case "char" -> "Character";
      default -> Character.toUpperCase(b.getName().charAt(0)) + b.getName().substring(1);
    };
  }

  /**
   * The type variables a stub in {@code owner} can name: its own, and those of the types around it,
   * up to one that is static.
   */
  private static Set<String> typeVariablesOf(ITypeBinding owner) {
    Set<String> names = new HashSet<>();
    for (ITypeBinding t = owner; t != null; t = t.getDeclaringClass()) {
      for (ITypeBinding v : t.getTypeParameters()) {
        names.add(v.getName());
      }
      if (Modifier.isStatic(t.getModifiers()) || t.isInterface() || t.isEnum()) {
        break;
      }
    }
    return names;
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

  /**
   * Whether {@code node} is in a static member of the named type around it, the one a bare call's
   * stub goes in: its member is looked for through anonymous classes and lambdas, whose own methods
   * do not make the call an instance one.
   */
  private static boolean inStaticContext(ASTNode node) {
    AbstractTypeDeclaration owner = Nodes.ancestorOrSelf(node, AbstractTypeDeclaration.class);
    for (ASTNode n = node.getParent(); n != null; n = n.getParent()) {
      if (n instanceof BodyDeclaration d && d.getParent() == owner) {
        return Modifier.isStatic(d.getModifiers());
      }
    }
    return false;
  }
}
