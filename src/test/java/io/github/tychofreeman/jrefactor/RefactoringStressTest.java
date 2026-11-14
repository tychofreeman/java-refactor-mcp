package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The refactorings called on random targets in a real project, through {@link Tools#call} as the
 * server calls them. Each must apply or refuse, never fail otherwise; an applied change must add no
 * compile error by a full count, and the narrowed count must agree; {@code undo} must put every
 * file back. Refusals are counted by tool and reason. Opt-in: {@code -Djrefactor.stress.root=<dir>}
 * (a copy: a killed run can leave a change behind), with {@code jrefactor.stress.calls} (default
 * 30), {@code jrefactor.stress.seed}, {@code jrefactor.stress.tools} (entries of {@link #TOOLS},
 * comma-separated; default all), {@code jrefactor.stress.counts=true} to print without failing, and
 * {@code jrefactor.stress.refusals=true} to print each refusal, and each applied call's result,
 * whole.
 */
class RefactoringStressTest {

  private static final List<String> TOOLS =
      List.of(
          "rename",
          "move_type",
          "add_parameter",
          "remove_parameter",
          "move_parameter",
          "extract_method",
          "extract_variable",
          "inline_variable",
          "inline_function",
          "pull_up",
          "push_down",
          "convert_lambda",
          "function_to_method",
          "extract_type",
          "convert_to_record",
          "extract_supertype",
          "move_type_package",
          "extract_variable_constant",
          "add_parameter_type_class",
          "add_parameter_type_method",
          "add_parameter_enum_values",
          "remove_parameter_value",
          "change_type_bound",
          "change_type_wildcard",
          "type_switch_to_method_enum",
          "extract_variable_parameter_all",
          "extract_variable_parameter_call",
          "extract_supertype_generic");

  @Test
  @EnabledIfSystemProperty(named = "jrefactor.stress.root", matches = ".+")
  void refactoringsApplyOrRefuseAndUndo() throws Exception {
    SourceProject project =
        SourceProject.load(Path.of(System.getProperty("jrefactor.stress.root")));
    int calls = Integer.getInteger("jrefactor.stress.calls", 30);
    long seed = Long.getLong("jrefactor.stress.seed", System.nanoTime());
    boolean countsOnly = Boolean.getBoolean("jrefactor.stress.counts");
    boolean refusals = Boolean.getBoolean("jrefactor.stress.refusals");
    Random random = new Random(seed);
    StressSnapshot.recover(project.root());
    List<Path> files = project.javaFiles();
    StressSnapshot.take(project.root(), files);
    Map<TypeCheck.Problem, Integer> first = TypeCheck.errors(project);
    Map<Path, String> texts = texts(files);
    System.out.printf("stress: %d files, %d calls, seed %d%n", files.size(), calls, seed);

    Map<String, Map<String, Integer>> outcomes = new TreeMap<>();
    List<String> failures = new ArrayList<>();
    int made = 0;
    Hierarchy hierarchy = Hierarchy.of(project, texts);
    List<String> tools = tools();
    Map<String, Integer> noTarget = new TreeMap<>();
    int skipped = 0;
    int misses = 0;
    for (int tries = 0; made < calls && tries < calls * 200; tries++) {
      Path file = files.get(random.nextInt(files.size()));
      // Each tool in turn, so a tool with few targets is still called; one that finds none in
      // 200 files gives its turn to the next.
      String tool = tools.get((made + skipped) % tools.size());
      String text = texts.get(file);
      Map<String, Object> args =
          target(tool, project.relative(file), Jdt.parseSyntax(text), text, hierarchy, random);
      if (args == null) {
        if (++misses >= 200) {
          skipped++;
          noTarget.merge(tool, 1, Integer::sum);
          misses = 0;
        }
        continue;
      }
      misses = 0;
      made++;
      String what = tool + " " + args;
      Map<String, Integer> tally = outcomes.computeIfAbsent(tool, k -> new TreeMap<>());
      try {
        // An entry with a suffix is its tool with an option: move_type with package, ...
        String real =
            switch (tool) {
              case "move_type_package" -> "move_type";
              case "extract_variable_constant",
                  "extract_variable_parameter_all",
                  "extract_variable_parameter_call" ->
                  "extract_variable";
              case "add_parameter_type_class",
                  "add_parameter_type_method",
                  "add_parameter_enum_values" ->
                  "add_parameter";
              case "remove_parameter_value" -> "remove_parameter";
              case "change_type_bound", "change_type_wildcard" -> "change_type";
              case "type_switch_to_method_enum" -> "type_switch_to_method";
              case "extract_supertype_generic" -> "extract_supertype";
              default -> tool;
            };
        Outcome done = Tools.call(Tools.named(real), project, args);
        if (refusals) {
          System.out.println("stress applied: " + what + ": " + done.render().replace('\n', ' '));
        }
      } catch (Refusal r) {
        String reason = r.getMessage().replaceAll("'[^']*'|\"[^\"]*\"|\\b\\w*Zq\\b", "_");
        reason = reason.substring(0, Math.min(60, reason.length())).replaceAll("\\s+", " ");
        tally.merge("refused: " + reason, 1, Integer::sum);
        if (r.getMessage().contains("compile errors")) {
          // The gate caught what the tool wrote: a precondition the tool may be missing.
          System.out.println("stress gated: " + what + ": " + r.getMessage().replace('\n', ' '));
        } else if (refusals) {
          System.out.println("stress refused: " + what + ": " + r.getMessage().replace('\n', ' '));
        }
        continue;
      } catch (Exception | Error e) {
        tally.merge("failed", 1, Integer::sum);
        failures.add(what + ": " + e);
        restore(project, texts);
        continue;
      }
      tally.merge("applied", 1, Integer::sum);
      Map<TypeCheck.Problem, Integer> full = TypeCheck.uncached(project);
      List<TypeCheck.Added> added = TypeCheck.introduced(first, full);
      if (!added.isEmpty()) {
        failures.add(what + ": added " + added);
      }
      if (!full.equals(TypeCheck.errors(project))) {
        failures.add(what + ": the narrowed count is not the full one");
      }
      try {
        Journal.undo(project);
      } catch (Exception e) {
        failures.add(what + ": undo failed: " + e);
        restore(project, texts);
        continue;
      }
      List<String> differ = differ(project, texts);
      if (!differ.isEmpty()) {
        failures.add(what + ": undo left " + differ);
        restore(project, texts);
      }
      if (!TypeCheck.uncached(project).equals(first)) {
        failures.add(what + ": the count after undo is not the first one");
      }
    }
    System.out.printf("stress: %d calls%n", made);
    outcomes.forEach((tool, tally) ->
        tally.forEach((k, n) -> System.out.printf("stress: %s %s: %d%n", tool, k, n)));
    noTarget.forEach((tool, n) ->
        System.out.printf("stress: %s found no target in 200 files, %d times%n", tool, n));
    StressSnapshot.done(project.root());
    failures.forEach(f -> System.out.println("stress failure: " + f));
    if (!countsOnly) {
      assertEquals(List.of(), failures, "seed " + seed);
    }
  }

  /** Arguments for {@code tool} on a random fitting target in {@code unit}, or null if none. */
  private static Map<String, Object> target(
      String tool,
      String rel,
      CompilationUnit unit,
      String text,
      Hierarchy hierarchy,
      Random random) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("file", rel);
    switch (tool) {
      case "rename" -> {
        // By kind first, so the many locals do not crowd out fields, methods and types.
        Map<String, List<SimpleName>> byKind = new TreeMap<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public boolean visit(SimpleName n) {
                if (n.isDeclaration()) {
                  byKind.computeIfAbsent(kind(n), k -> new ArrayList<>()).add(n);
                }
                return true;
              }
            });
        if (byKind.isEmpty()) {
          return null;
        }
        List<List<SimpleName>> kinds = new ArrayList<>(byKind.values());
        List<SimpleName> names = kinds.get(random.nextInt(kinds.size()));
        SimpleName n = names.get(random.nextInt(names.size()));
        args.put("line", unit.getLineNumber(n.getStartPosition()));
        args.put("name", n.getIdentifier());
        args.put("new_name", n.getIdentifier() + "Zq");
      }
      case "move_type" -> {
        List<AbstractTypeDeclaration> nested = new ArrayList<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public void endVisit(TypeDeclaration t) {
                add(t);
              }

              @Override
              public void endVisit(EnumDeclaration t) {
                add(t);
              }

              @Override
              public void endVisit(RecordDeclaration t) {
                add(t);
              }

              private void add(AbstractTypeDeclaration t) {
                if (t.isMemberTypeDeclaration()) {
                  nested.add(t);
                }
              }
            });
        if (nested.isEmpty()) {
          return null;
        }
        args.put("name", typePath(nested.get(random.nextInt(nested.size()))));
      }
      case "add_parameter", "remove_parameter", "move_parameter" -> {
        List<MethodDeclaration> methods = new ArrayList<>();
        for (MethodDeclaration m : methods(unit)) {
          int size = m.parameters().size();
          if (!m.isConstructor()
              && (tool.equals("add_parameter")
                  || (tool.equals("remove_parameter") && size >= 1)
                  || (tool.equals("move_parameter") && size >= 2))) {
            methods.add(m);
          }
        }
        if (methods.isEmpty()) {
          return null;
        }
        MethodDeclaration m = methods.get(random.nextInt(methods.size()));
        args.put("name", methodName(m));
        List<?> params = m.parameters();
        switch (tool) {
          case "add_parameter" -> {
            args.put("parameter", "zq");
            args.put("type", "int");
            args.put("value", "0");
          }
          case "remove_parameter" ->
              args.put("parameter", paramName(params.get(random.nextInt(params.size()))));
          default -> {
            args.put("parameter", paramName(params.get(params.size() - 1)));
            args.put("before", paramName(params.get(0)));
          }
        }
      }
      case "extract_method" -> {
        List<MethodDeclaration> bodies = new ArrayList<>();
        for (MethodDeclaration m : methods(unit)) {
          if (m.getBody() != null && !m.getBody().statements().isEmpty()) {
            bodies.add(m);
          }
        }
        if (bodies.isEmpty()) {
          return null;
        }
        Block body = bodies.get(random.nextInt(bodies.size())).getBody();
        List<?> statements = body.statements();
        int from = random.nextInt(statements.size());
        int to = from + random.nextInt(statements.size() - from);
        Statement a = (Statement) statements.get(from);
        Statement b = (Statement) statements.get(to);
        args.put("start_line", unit.getLineNumber(a.getStartPosition()));
        args.put("end_line", unit.getLineNumber(Nodes.end(b) - 1));
        args.put("name", "extractedZq");
      }
      case "extract_variable" -> {
        List<Expression> found = new ArrayList<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public void preVisit(ASTNode n) {
                if ((n instanceof InfixExpression
                        || n instanceof CastExpression
                        || n instanceof FieldAccess
                        || n instanceof ArrayAccess)
                    && pure(n)
                    && statementOf(n) != null
                    && statementOf(n).getParent() instanceof Block) {
                  found.add((Expression) n);
                }
              }
            });
        if (found.isEmpty()) {
          return null;
        }
        Expression e = found.get(random.nextInt(found.size()));
        int start = e.getStartPosition();
        args.put("line", unit.getLineNumber(start));
        args.put("column", unit.getColumnNumber(start) + 1);
        args.put("expression", text.substring(start, start + e.getLength()));
        args.put("name", "varZq");
      }
      case "inline_variable" -> {
        List<VariableDeclarationFragment> locals = new ArrayList<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public boolean visit(VariableDeclarationStatement s) {
                for (Object f : s.fragments()) {
                  if (((VariableDeclarationFragment) f).getInitializer() != null
                      && pure(((VariableDeclarationFragment) f).getInitializer())) {
                    locals.add((VariableDeclarationFragment) f);
                  }
                }
                return true;
              }
            });
        if (locals.isEmpty()) {
          return null;
        }
        VariableDeclarationFragment f = locals.get(random.nextInt(locals.size()));
        args.put("line", unit.getLineNumber(f.getName().getStartPosition()));
        args.put("name", f.getName().getIdentifier());
      }
      case "inline_function" -> {
        List<MethodDeclaration> statics = new ArrayList<>();
        for (MethodDeclaration m : methods(unit)) {
          if (!m.isConstructor() && m.getBody() != null && m.getParent().getParent() == unit) {
            statics.add(m);
          }
        }
        if (statics.isEmpty()) {
          return null;
        }
        MethodDeclaration m = statics.get(random.nextInt(statics.size()));
        args.put("name", typePath(m.getParent()) + "." + m.getName().getIdentifier());
      }
      case "pull_up", "push_down" -> {
        // Members of a top-level class: push_down's one that a project class extends, pull_up's
        // one that extends a project class.
        List<String> members = new ArrayList<>();
        for (Object t : unit.types()) {
          if (t instanceof TypeDeclaration c
              && !c.isInterface()
              && (tool.equals("push_down")
                  ? hierarchy.extended().contains(c.getName().getIdentifier())
                  : hierarchy.superclassIn(c))) {
            for (MethodDeclaration m : c.getMethods()) {
              if (!m.isConstructor()) {
                members.add(methodName(m));
              }
            }
            for (FieldDeclaration fd : c.getFields()) {
              for (Object v : fd.fragments()) {
                members.add(
                    c.getName().getIdentifier()
                        + "."
                        + ((VariableDeclarationFragment) v).getName().getIdentifier());
              }
            }
          }
        }
        if (members.isEmpty()) {
          return null;
        }
        args.put("name", members.get(random.nextInt(members.size())));
      }
      case "convert_lambda" -> {
        List<ASTNode> found = new ArrayList<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public void preVisit(ASTNode n) {
                if (n instanceof LambdaExpression
                    || n instanceof MethodReference
                    || (n instanceof ClassInstanceCreation c
                        && c.getAnonymousClassDeclaration() != null)) {
                  found.add(n);
                }
              }
            });
        if (found.isEmpty()) {
          return null;
        }
        ASTNode n = found.get(random.nextInt(found.size()));
        args.put("line", unit.getLineNumber(n.getStartPosition()));
        args.put("column", unit.getColumnNumber(n.getStartPosition()) + 1);
        args.put(
            "to",
            n instanceof LambdaExpression
                ? (random.nextBoolean() ? "method_reference" : "anonymous")
                : "lambda");
      }
      case "function_to_method" -> {
        List<MethodDeclaration> candidates = new ArrayList<>();
        for (MethodDeclaration m : methods(unit)) {
          if (Modifier.isStatic(m.getModifiers())
              && !m.parameters().isEmpty()
              && m.getParent() instanceof TypeDeclaration t
              && t.getParent() == unit
              && ((SingleVariableDeclaration) m.parameters().get(0))
                  .getType()
                  .toString()
                  .equals(t.getName().getIdentifier())) {
            candidates.add(m);
          }
        }
        if (candidates.isEmpty()) {
          return null;
        }
        args.put("name", methodName(candidates.get(random.nextInt(candidates.size()))));
      }
      case "extract_type" -> {
        List<MethodDeclaration> candidates = new ArrayList<>();
        for (MethodDeclaration m : methods(unit)) {
          if (m.parameters().size() >= 2) {
            candidates.add(m);
          }
        }
        if (candidates.isEmpty()) {
          return null;
        }
        MethodDeclaration m = candidates.get(random.nextInt(candidates.size()));
        int at = random.nextInt(m.parameters().size() - 1);
        args.put("name", methodName(m));
        args.put(
            "parameters",
            List.of(paramName(m.parameters().get(at)), paramName(m.parameters().get(at + 1))));
        args.put("new_type", "PairZq");
        args.put("parameter", "pairZq");
      }
      case "convert_to_record", "extract_supertype", "move_type_package" -> {
        List<TypeDeclaration> classes = new ArrayList<>();
        for (Object t : unit.types()) {
          if (t instanceof TypeDeclaration c && !c.isInterface()) {
            classes.add(c);
          }
        }
        if (classes.isEmpty() || unit.getPackage() == null) {
          return null;
        }
        TypeDeclaration c = classes.get(random.nextInt(classes.size()));
        args.put("name", c.getName().getIdentifier());
        switch (tool) {
          case "convert_to_record" -> args.put("value_semantics", true);
          case "extract_supertype" -> {
            List<String> publics = new ArrayList<>();
            for (MethodDeclaration m : c.getMethods()) {
              if (Modifier.isPublic(m.getModifiers())
                  && !Modifier.isStatic(m.getModifiers())
                  && !m.isConstructor()) {
                publics.add(m.getName().getIdentifier());
              }
            }
            if (publics.isEmpty()) {
              return null;
            }
            args.put("supertype", "IfaceZq");
            args.put("kind", "interface");
            args.put("members", List.of(publics.get(random.nextInt(publics.size()))));
          }
          default ->
              args.put("package", unit.getPackage().getName().getFullyQualifiedName() + ".zq");
        }
      }
      case "extract_variable_constant" -> {
        List<NumberLiteral> literals = new ArrayList<>();
        unit.accept(
            new ASTVisitor() {
              @Override
              public boolean visit(NumberLiteral n) {
                if (statementOf(n) != null && statementOf(n).getParent() instanceof Block) {
                  literals.add(n);
                }
                return true;
              }
            });
        if (literals.isEmpty()) {
          return null;
        }
        NumberLiteral n = literals.get(random.nextInt(literals.size()));
        args.put("line", unit.getLineNumber(n.getStartPosition()));
        args.put("column", unit.getColumnNumber(n.getStartPosition()) + 1);
        args.put("expression", n.getToken());
        args.put("name", "CONST_ZQ");
        args.put("as", "constant");
      }
      case "add_parameter_type_class",
          "add_parameter_type_method",
          "add_parameter_enum_values",
          "remove_parameter_value",
          "change_type_bound",
          "change_type_wildcard",
          "type_switch_to_method_enum",
          "extract_variable_parameter_all",
          "extract_variable_parameter_call",
          "extract_supertype_generic" -> {
        Map<String, Object> option = newOption(tool, unit, text, hierarchy, random);
        if (option == null) {
          return null;
        }
        args.putAll(option);
      }
      default -> throw new IllegalArgumentException(tool);
    }
    return args;
  }

  /** What a declared name declares: "type", "method", "field", or "local" (a parameter too). */
  private static String kind(SimpleName n) {
    ASTNode parent = n.getParent();
    if (parent instanceof AbstractTypeDeclaration) {
      return "type";
    }
    if (parent instanceof MethodDeclaration) {
      return "method";
    }
    if (parent.getParent() instanceof FieldDeclaration) {
      return "field";
    }
    return "local";
  }

  private static List<MethodDeclaration> methods(CompilationUnit unit) {
    List<MethodDeclaration> out = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodDeclaration m) {
            if (m.getParent() instanceof AbstractTypeDeclaration
                && !Modifier.isAbstract(m.getModifiers())) {
              out.add(m);
            }
            return true;
          }
        });
    return out;
  }

  /** The statement {@code n} is in, or null outside one (a field's initializer). */
  private static Statement statementOf(ASTNode n) {
    while (n != null && !(n instanceof Statement)) {
      n = n.getParent();
    }
    return (Statement) n;
  }

  /** Whether {@code n} holds no call, assignment, increment or object creation. */
  private static boolean pure(ASTNode n) {
    boolean[] pure = {true};
    n.accept(
        new ASTVisitor() {
          @Override
          public void preVisit(ASTNode inner) {
            if (inner instanceof MethodInvocation
                || inner instanceof SuperMethodInvocation
                || inner instanceof ClassInstanceCreation
                || inner instanceof Assignment
                || inner instanceof PostfixExpression
                || (inner instanceof PrefixExpression p
                    && (p.getOperator() == PrefixExpression.Operator.INCREMENT
                        || p.getOperator() == PrefixExpression.Operator.DECREMENT))) {
              pure[0] = false;
            }
          }
        });
    return pure[0];
  }

  /** "Outer.Inner" for a member type. */
  private static String typePath(ASTNode type) {
    List<String> parts = new ArrayList<>();
    for (ASTNode n = type; n instanceof AbstractTypeDeclaration t; n = n.getParent()) {
      parts.add(0, t.getName().getIdentifier());
    }
    return String.join(".", parts);
  }

  /** "Outer.Inner.m(int, String)", as the signature tools name a method. */
  private static String methodName(MethodDeclaration m) {
    List<String> types = new ArrayList<>();
    for (Object o : m.parameters()) {
      SingleVariableDeclaration p = (SingleVariableDeclaration) o;
      types.add(p.getType() + (p.isVarargs() ? "..." : ""));
    }
    return typePath(m.getParent())
        + "."
        + m.getName().getIdentifier()
        + "("
        + String.join(", ", types)
        + ")";
  }

  private static String paramName(Object p) {
    return ((SingleVariableDeclaration) p).getName().getIdentifier();
  }

  private static Map<Path, String> texts(List<Path> files) throws Exception {
    Map<Path, String> out = new HashMap<>();
    for (Path f : files) {
      out.put(f, Files.readString(f));
    }
    return out;
  }

  /** The files whose text is not what {@code texts} holds, and the Java files not in it. */
  private static List<String> differ(SourceProject project, Map<Path, String> texts)
      throws Exception {
    List<String> out = new ArrayList<>();
    for (Map.Entry<Path, String> e : texts.entrySet()) {
      if (!Files.exists(e.getKey())) {
        out.add(project.relative(e.getKey()) + " (gone)");
      } else if (!Files.readString(e.getKey()).equals(e.getValue())) {
        out.add(project.relative(e.getKey()));
      }
    }
    for (Path p : project.javaFiles()) {
      if (!texts.containsKey(p)) {
        out.add(project.relative(p) + " (new)");
      }
    }
    return out;
  }

  /** Puts back every file of {@code texts}, and removes the Java files it does not hold. */
  private static void restore(SourceProject project, Map<Path, String> texts) throws Exception {
    for (Map.Entry<Path, String> e : texts.entrySet()) {
      if (!Files.exists(e.getKey()) || !Files.readString(e.getKey()).equals(e.getValue())) {
        Files.writeString(e.getKey(), e.getValue());
      }
    }
    for (Path p : project.javaFiles()) {
      if (!texts.containsKey(p)) {
        Files.delete(p);
      }
    }
  }

  /**
   * The project's top-level class names, the simple names its classes extend, and its methods by
   * name and arity ("indexOf/2"): the declaring file, the method as the signature tools name it,
   * then its parameters' names; empty when two methods share the key.
   */
  private record Hierarchy(
      Set<String> classes, Set<String> extended, Map<String, List<String>> declared) {
    static Hierarchy of(SourceProject project, Map<Path, String> texts) {
      Set<String> classes = new HashSet<>();
      Set<String> extended = new HashSet<>();
      Map<String, List<String>> declared = new HashMap<>();
      for (Map.Entry<Path, String> e : texts.entrySet()) {
        CompilationUnit unit = Jdt.parseSyntax(e.getValue());
        for (Object t : unit.types()) {
          if (t instanceof TypeDeclaration c && !c.isInterface()) {
            classes.add(c.getName().getIdentifier());
            if (c.getSuperclassType() != null) {
              extended.add(simple(c.getSuperclassType()));
            }
          }
        }
        for (MethodDeclaration m : methods(unit)) {
          if (!m.isConstructor() && !m.isVarargs()) {
            List<String> entry = new ArrayList<>();
            entry.add(project.relative(e.getKey()));
            entry.add(methodName(m));
            for (Object p : m.parameters()) {
              entry.add(paramName(p));
            }
            declared.merge(
                m.getName().getIdentifier() + "/" + m.parameters().size(),
                entry,
                (a, b) -> List.of());
          }
        }
      }
      return new Hierarchy(classes, extended, declared);
    }

    /** Whether {@code c} extends a class of the project. */
    boolean superclassIn(TypeDeclaration c) {
      return c.getSuperclassType() != null && classes.contains(simple(c.getSuperclassType()));
    }

    private static String simple(Type type) {
      String name = type.toString().replaceAll("<.*", "");
      return Names.simpleName(name);
    }
  }

  /** {@link #TOOLS}, or those {@code jrefactor.stress.tools} names (comma-separated). */
  private static List<String> tools() {
    String named = System.getProperty("jrefactor.stress.tools", "");
    if (named.isBlank()) {
      return TOOLS;
    }
    List<String> tools = List.of(named.split(","));
    for (String t : tools) {
      if (!TOOLS.contains(t)) {
        throw new IllegalArgumentException("no stress tool " + t + "; the tools are " + TOOLS);
      }
    }
    return tools;
  }

  /** Arguments for an option merged on 2026-11-13, named by {@code tool}, or null if none fits. */
  private static Map<String, Object> newOption(
      String tool, CompilationUnit unit, String text, Hierarchy hierarchy, Random random) {
    return switch (tool) {
      case "add_parameter_type_class" -> typeParameterOfClass(unit, random);
      case "add_parameter_type_method" -> typeParameterOfMethod(unit, random);
      case "add_parameter_enum_values" -> enumValues(unit, random);
      case "remove_parameter_value" -> parameterValue(unit, text, hierarchy, random);
      case "change_type_bound" -> bound(unit, random);
      case "change_type_wildcard" -> wildcard(unit, text, random);
      case "type_switch_to_method_enum" -> enumSwitch(unit, random);
      case "extract_variable_parameter_all" -> parameterExpression(unit, text, false, random);
      case "extract_variable_parameter_call" -> parameterExpression(unit, text, true, random);
      case "extract_supertype_generic" -> genericSupertype(unit, random);
      default -> throw new IllegalArgumentException(tool);
    };
  }

  /**
   * add_parameter with type_parameter on a class: {@code value} a type its instance members'
   * declared types name, and half the time {@code members} one or two of the members naming it.
   */
  private static Map<String, Object> typeParameterOfClass(CompilationUnit unit, Random random) {
    List<TypeDeclaration> classes = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(TypeDeclaration t) {
            if (!t.isInterface() && !t.isLocalTypeDeclaration()) {
              classes.add(t);
            }
            return true;
          }
        });
    if (classes.isEmpty()) {
      return null;
    }
    TypeDeclaration c = classes.get(random.nextInt(classes.size()));
    // Each simple type name the instance members declare, and the members that name it.
    Map<String, List<String>> byType = new TreeMap<>();
    for (FieldDeclaration f : c.getFields()) {
      if (!Modifier.isStatic(f.getModifiers())) {
        for (String t : simpleTypes(f.getType())) {
          List<String> members = byType.computeIfAbsent(t, k -> new ArrayList<>());
          if (f.fragments().size() == 1) {
            members.add(
                ((VariableDeclarationFragment) f.fragments().get(0)).getName().getIdentifier());
          }
        }
      }
    }
    for (MethodDeclaration m : c.getMethods()) {
      if (!Modifier.isStatic(m.getModifiers())) {
        Set<String> types = new TreeSet<>();
        for (Object p : m.parameters()) {
          types.addAll(simpleTypes(((SingleVariableDeclaration) p).getType()));
        }
        if (m.getReturnType2() != null) {
          types.addAll(simpleTypes(m.getReturnType2()));
        }
        for (String t : types) {
          byType.computeIfAbsent(t, k -> new ArrayList<>()).add(memberName(c, m));
        }
      }
    }
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", typePath(c));
    args.put("type_parameter", "Zq");
    if (byType.isEmpty()) {
      args.put("value", "String");
      return args;
    }
    List<String> types = new ArrayList<>(byType.keySet());
    String value = types.get(random.nextInt(types.size()));
    args.put("value", value);
    List<String> members = byType.get(value);
    if (!members.isEmpty() && random.nextBoolean()) {
      args.put("members", some(members, random));
    }
    return args;
  }

  /** add_parameter with type_parameter on a method: {@code value} a type its parameters name. */
  private static Map<String, Object> typeParameterOfMethod(CompilationUnit unit, Random random) {
    Map<MethodDeclaration, List<String>> found = new LinkedHashMap<>();
    for (MethodDeclaration m : methods(unit)) {
      if (!m.isConstructor() && m.getParent() instanceof TypeDeclaration) {
        Set<String> types = new TreeSet<>();
        for (Object p : m.parameters()) {
          types.addAll(simpleTypes(((SingleVariableDeclaration) p).getType()));
        }
        if (!types.isEmpty()) {
          found.put(m, new ArrayList<>(types));
        }
      }
    }
    if (found.isEmpty()) {
      return null;
    }
    List<MethodDeclaration> candidates = new ArrayList<>(found.keySet());
    MethodDeclaration m = candidates.get(random.nextInt(candidates.size()));
    List<String> types = found.get(m);
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", methodName(m));
    args.put("type_parameter", "Zq");
    args.put("value", types.get(random.nextInt(types.size())));
    return args;
  }

  /**
   * add_parameter on an enum with {@code values}: each constant its index, or a third of the time
   * one constant left to {@code value}.
   */
  private static Map<String, Object> enumValues(CompilationUnit unit, Random random) {
    List<EnumDeclaration> enums = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(EnumDeclaration e) {
            if (!e.isLocalTypeDeclaration() && !e.enumConstants().isEmpty()) {
              enums.add(e);
            }
            return true;
          }
        });
    if (enums.isEmpty()) {
      return null;
    }
    EnumDeclaration e = enums.get(random.nextInt(enums.size()));
    List<?> constants = e.enumConstants();
    int left = random.nextInt(3) == 0 ? random.nextInt(constants.size()) : -1;
    Map<String, Object> values = new LinkedHashMap<>();
    for (int i = 0; i < constants.size(); i++) {
      if (i != left) {
        values.put(
            ((EnumConstantDeclaration) constants.get(i)).getName().getIdentifier(),
            String.valueOf(i));
      }
    }
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", typePath(e));
    args.put("parameter", "zq");
    args.put("type", "int");
    args.put("values", values);
    if (left >= 0) {
      args.put("value", "-1");
    }
    return args;
  }

  /**
   * remove_parameter with an expression {@code value}, from a call in {@code unit} to a method the
   * project declares once by that name and arity: an argument that reads other arguments that are
   * names, written with the parameters they are passed for.
   */
  private static Map<String, Object> parameterValue(
      CompilationUnit unit, String text, Hierarchy hierarchy, Random random) {
    List<Map<String, Object>> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(MethodInvocation call) {
            List<?> arguments = call.arguments();
            List<String> declared =
                hierarchy.declared().get(call.getName().getIdentifier() + "/" + arguments.size());
            if (declared == null || declared.isEmpty()) {
              return true;
            }
            for (int i = 0; i < arguments.size(); i++) {
              Map<String, String> names = new HashMap<>();
              for (int j = 0; j < arguments.size(); j++) {
                if (j != i && arguments.get(j) instanceof SimpleName n) {
                  names.putIfAbsent(n.getIdentifier(), declared.get(2 + j));
                }
              }
              String value = rewritten((Expression) arguments.get(i), text, names);
              if (value != null) {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("file", declared.get(0));
                args.put("name", declared.get(1));
                args.put("parameter", declared.get(2 + i));
                args.put("value", value);
                found.add(args);
              }
            }
            return true;
          }
        });
    return found.isEmpty() ? null : found.get(random.nextInt(found.size()));
  }

  /**
   * The text of {@code e} with each name of {@code names} it reads as a variable replaced by its
   * entry, or null if it reads none.
   */
  private static String rewritten(Expression e, String text, Map<String, String> names) {
    List<SimpleName> reads = new ArrayList<>();
    e.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName n) {
            if (names.containsKey(n.getIdentifier())
                && n.getLocationInParent() != MethodInvocation.NAME_PROPERTY
                && n.getLocationInParent() != QualifiedName.NAME_PROPERTY
                && n.getLocationInParent() != FieldAccess.NAME_PROPERTY
                && !(n.getParent() instanceof SimpleType)) {
              reads.add(n);
            }
            return true;
          }
        });
    if (reads.isEmpty()) {
      return null;
    }
    int start = e.getStartPosition();
    StringBuilder out = new StringBuilder(text.substring(start, start + e.getLength()));
    for (int k = reads.size() - 1; k >= 0; k--) {
      SimpleName n = reads.get(k);
      int at = n.getStartPosition() - start;
      out.replace(at, at + n.getLength(), names.get(n.getIdentifier()));
    }
    return out.toString();
  }

  /** change_type with type_parameter: a new bound for a type parameter of a class or a method. */
  private static Map<String, Object> bound(CompilationUnit unit, Random random) {
    List<ASTNode> generic = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(TypeDeclaration t) {
            if (!t.isLocalTypeDeclaration() && !t.typeParameters().isEmpty()) {
              generic.add(t);
            }
            return true;
          }

          @Override
          public boolean visit(MethodDeclaration m) {
            if (!m.isConstructor()
                && m.getParent() instanceof AbstractTypeDeclaration
                && !m.typeParameters().isEmpty()) {
              generic.add(m);
            }
            return true;
          }
        });
    if (generic.isEmpty()) {
      return null;
    }
    ASTNode n = generic.get(random.nextInt(generic.size()));
    List<?> parameters =
        n instanceof MethodDeclaration m
            ? m.typeParameters()
            : ((TypeDeclaration) n).typeParameters();
    String t =
        ((TypeParameter) parameters.get(random.nextInt(parameters.size())))
            .getName()
            .getIdentifier();
    List<String> bounds =
        List.of(
            "Object",
            "Comparable<" + t + ">",
            "Comparable<? super " + t + ">",
            "Object & Comparable<" + t + ">",
            "CharSequence");
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", n instanceof MethodDeclaration m ? methodName(m) : typePath(n));
    args.put("type_parameter", t);
    args.put("type", bounds.get(random.nextInt(bounds.size())));
    return args;
  }

  /** change_type of a parameter: one type argument of its type made a wildcard. */
  private static Map<String, Object> wildcard(CompilationUnit unit, String text, Random random) {
    List<Type> found = new ArrayList<>();
    for (MethodDeclaration m : methods(unit)) {
      if (!m.isConstructor()) {
        for (Object o : m.parameters()) {
          if (o instanceof SingleVariableDeclaration p
              && !p.isVarargs()
              && p.getType() instanceof ParameterizedType t) {
            for (Object a : t.typeArguments()) {
              if (!(a instanceof WildcardType)) {
                found.add((Type) a);
              }
            }
          }
        }
      }
    }
    if (found.isEmpty()) {
      return null;
    }
    Type a = found.get(random.nextInt(found.size()));
    SingleVariableDeclaration p = (SingleVariableDeclaration) a.getParent().getParent();
    int start = p.getType().getStartPosition();
    String type =
        text.substring(start, a.getStartPosition())
            + (random.nextBoolean() ? "? extends " : "? super ")
            + text.substring(a.getStartPosition(), start + p.getType().getLength());
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", methodName((MethodDeclaration) p.getParent()));
    args.put("parameter", p.getName().getIdentifier());
    args.put("type", type);
    return args;
  }

  /**
   * type_switch_to_method on a switch expression, or a switch statement of arrow cases, whose cases
   * name constants; {@code as} field, method or left out.
   */
  private static Map<String, Object> enumSwitch(CompilationUnit unit, Random random) {
    List<ASTNode> found = new ArrayList<>();
    unit.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SwitchExpression s) {
            if (constantCases(s.statements(), true)) {
              found.add(s);
            }
            return true;
          }

          @Override
          public boolean visit(SwitchStatement s) {
            if (constantCases(s.statements(), false)) {
              found.add(s);
            }
            return true;
          }
        });
    if (found.isEmpty()) {
      return null;
    }
    ASTNode s = found.get(random.nextInt(found.size()));
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("line", unit.getLineNumber(s.getStartPosition()));
    args.put("name", "zqName");
    switch (random.nextInt(3)) {
      case 1 -> args.put("as", "field");
      case 2 -> args.put("as", "method");
      default -> {}
    }
    return args;
  }

  /**
   * Whether every case of {@code statements} names constants (a default aside), each an arrow case
   * unless the switch is an {@code expression}.
   */
  private static boolean constantCases(List<?> statements, boolean expression) {
    boolean any = false;
    for (Object o : statements) {
      if (o instanceof SwitchCase c) {
        if (!expression && !c.isSwitchLabeledRule()) {
          return false;
        }
        for (Object e : c.expressions()) {
          if (!(e instanceof SimpleName)) {
            return false;
          }
          any = true;
        }
      }
    }
    return any;
  }

  /**
   * extract_variable as=parameter: with all_occurrences, an infix expression of the method's
   * parameters, constants and literals (one that occurs twice, most of the time); or a call on a
   * parameter or a class with such arguments.
   */
  private static Map<String, Object> parameterExpression(
      CompilationUnit unit, String text, boolean call, Random random) {
    List<Expression> found = new ArrayList<>();
    List<Expression> twice = new ArrayList<>();
    for (MethodDeclaration m : methods(unit)) {
      if (m.isConstructor() || m.getBody() == null || m.parameters().isEmpty()) {
        continue;
      }
      Set<String> parameters = new HashSet<>();
      for (Object p : m.parameters()) {
        parameters.add(paramName(p));
      }
      Map<String, List<Expression>> byText = new HashMap<>();
      m.getBody()
          .accept(
              new ASTVisitor() {
                @Override
                public void preVisit(ASTNode n) {
                  boolean fits =
                      call
                          ? n instanceof MethodInvocation i
                              && i.getExpression() instanceof SimpleName r
                              && (parameters.contains(r.getIdentifier())
                                  || Character.isUpperCase(r.getIdentifier().charAt(0)))
                              && ((List<?>) i.arguments())
                                  .stream().allMatch(a -> fixed((ASTNode) a, parameters))
                          : n instanceof InfixExpression
                              && fixed(n, parameters)
                              && reads(n, parameters);
                  if (fits) {
                    int start = n.getStartPosition();
                    byText
                        .computeIfAbsent(
                            text.substring(start, start + n.getLength()), k -> new ArrayList<>())
                        .add((Expression) n);
                  }
                }
              });
      for (List<Expression> same : byText.values()) {
        found.addAll(same);
        if (same.size() > 1) {
          twice.addAll(same);
        }
      }
    }
    if (found.isEmpty()) {
      return null;
    }
    List<Expression> from = !twice.isEmpty() && random.nextInt(10) < 7 ? twice : found;
    Expression e = from.get(random.nextInt(from.size()));
    int start = e.getStartPosition();
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("line", unit.getLineNumber(start));
    args.put("column", unit.getColumnNumber(start) + 1);
    args.put("expression", text.substring(start, start + e.getLength()));
    args.put("name", "paramZq");
    args.put("as", "parameter");
    if (!call || random.nextInt(10) < 3) {
      args.put("all_occurrences", true);
    }
    return args;
  }

  /**
   * Whether {@code n} reads only {@code parameters}, constants (names in capitals) and literals,
   * with no call or assignment.
   */
  private static boolean fixed(ASTNode n, Set<String> parameters) {
    if (!pure(n)) {
      return false;
    }
    boolean[] fixed = {true};
    n.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName s) {
            String id = s.getIdentifier();
            if (s.getLocationInParent() != QualifiedName.QUALIFIER_PROPERTY
                && !parameters.contains(id)
                && !id.matches("[A-Z][A-Z0-9_]*")) {
              fixed[0] = false;
            }
            return true;
          }
        });
    return fixed[0];
  }

  /** Whether {@code n} names one of {@code parameters}. */
  private static boolean reads(ASTNode n, Set<String> parameters) {
    boolean[] reads = {false};
    n.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleName s) {
            reads[0] |= parameters.contains(s.getIdentifier());
            return true;
          }
        });
    return reads[0];
  }

  /**
   * extract_supertype from a generic top-level class: one or two of its public instance methods as
   * an interface, or of its instance methods and fields as a class.
   */
  private static Map<String, Object> genericSupertype(CompilationUnit unit, Random random) {
    List<TypeDeclaration> classes = new ArrayList<>();
    for (Object t : unit.types()) {
      if (t instanceof TypeDeclaration c && !c.isInterface() && !c.typeParameters().isEmpty()) {
        classes.add(c);
      }
    }
    if (classes.isEmpty() || unit.getPackage() == null) {
      return null;
    }
    TypeDeclaration c = classes.get(random.nextInt(classes.size()));
    boolean asInterface = random.nextBoolean();
    List<String> members = new ArrayList<>();
    for (MethodDeclaration m : c.getMethods()) {
      if (!m.isConstructor()
          && !Modifier.isStatic(m.getModifiers())
          && (!asInterface || Modifier.isPublic(m.getModifiers()))) {
        members.add(memberName(c, m));
      }
    }
    if (!asInterface) {
      for (FieldDeclaration f : c.getFields()) {
        if (!Modifier.isStatic(f.getModifiers())) {
          for (Object v : f.fragments()) {
            members.add(((VariableDeclarationFragment) v).getName().getIdentifier());
          }
        }
      }
    }
    if (members.isEmpty()) {
      return null;
    }
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("name", c.getName().getIdentifier());
    args.put("supertype", "SuperZq");
    args.put("kind", asInterface ? "interface" : "class");
    args.put("members", some(members, random));
    return args;
  }

  /** The simple names of the types written in {@code type}, the type variables in scope aside. */
  private static Set<String> simpleTypes(Type type) {
    Set<String> variables = typeVariables(type);
    Set<String> out = new TreeSet<>();
    type.accept(
        new ASTVisitor() {
          @Override
          public boolean visit(SimpleType t) {
            if (t.getName() instanceof SimpleName n && !variables.contains(n.getIdentifier())) {
              out.add(n.getIdentifier());
            }
            return true;
          }
        });
    return out;
  }

  /** The names of the type parameters declared around {@code n}. */
  private static Set<String> typeVariables(ASTNode n) {
    Set<String> out = new HashSet<>();
    for (ASTNode at = n; at != null; at = at.getParent()) {
      List<?> parameters =
          at instanceof TypeDeclaration t
              ? t.typeParameters()
              : at instanceof MethodDeclaration m ? m.typeParameters() : List.of();
      for (Object p : parameters) {
        out.add(((TypeParameter) p).getName().getIdentifier());
      }
    }
    return out;
  }

  /**
   * A member of {@code c} as {@code members} names it: {@code get}, or {@code get(int)} when the
   * name is overloaded; a constructor as {@code Box(String)}.
   */
  private static String memberName(TypeDeclaration c, MethodDeclaration m) {
    String name = m.getName().getIdentifier();
    int same = 0;
    for (MethodDeclaration o : c.getMethods()) {
      if (o.getName().getIdentifier().equals(name)) {
        same++;
      }
    }
    if (!m.isConstructor() && same == 1) {
      return name;
    }
    String full = methodName(m);
    return full.substring(full.lastIndexOf('.', full.indexOf('(')) + 1);
  }

  /** One or two distinct entries of {@code from}, at random. */
  private static List<String> some(List<String> from, Random random) {
    List<String> distinct = new ArrayList<>(new TreeSet<>(from));
    Collections.shuffle(distinct, random);
    return List.copyOf(distinct.subList(0, Math.min(distinct.size(), 1 + random.nextInt(2))));
  }
}
