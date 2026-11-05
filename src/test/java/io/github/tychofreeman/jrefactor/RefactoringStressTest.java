package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The refactorings called on random targets in a real project, through {@link Tools#call} as the
 * server calls them. Each must apply or refuse, never fail otherwise; an applied change must add no
 * compile error by a full count, and the narrowed count must agree; {@code undo} must put every
 * file back. Refusals are counted by tool and reason. Opt-in: {@code -Djrefactor.stress.root=<dir>}
 * (a copy: a killed run can leave a change behind), with {@code jrefactor.stress.calls} (default
 * 30), {@code jrefactor.stress.seed}, and {@code jrefactor.stress.counts=true} to print without
 * failing.
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
          "extract_struct",
          "convert_to_record",
          "extract_supertype",
          "move_type_package",
          "extract_variable_constant");

  @Test
  @EnabledIfSystemProperty(named = "jrefactor.stress.root", matches = ".+")
  void refactoringsApplyOrRefuseAndUndo() throws Exception {
    SourceProject project =
        SourceProject.load(Path.of(System.getProperty("jrefactor.stress.root")));
    int calls = Integer.getInteger("jrefactor.stress.calls", 30);
    long seed = Long.getLong("jrefactor.stress.seed", System.nanoTime());
    boolean countsOnly = Boolean.getBoolean("jrefactor.stress.counts");
    Random random = new Random(seed);
    StressSnapshot.recover(project.root());
    List<Path> files = project.javaFiles();
    StressSnapshot.take(project.root(), files);
    Map<String, Integer> first = TypeCheck.errors(project);
    Map<Path, String> texts = texts(files);
    System.out.printf("stress: %d files, %d calls, seed %d%n", files.size(), calls, seed);

    Map<String, Map<String, Integer>> outcomes = new TreeMap<>();
    List<String> failures = new ArrayList<>();
    int made = 0;
    Hierarchy hierarchy = Hierarchy.of(texts.values());
    for (int tries = 0; made < calls && tries < calls * 200; tries++) {
      Path file = files.get(random.nextInt(files.size()));
      // Each tool in turn, so a tool with few targets is still called.
      String tool = TOOLS.get(made % TOOLS.size());
      String text = texts.get(file);
      Map<String, Object> args =
          target(tool, project.relative(file), Jdt.parseSyntax(text), text, hierarchy, random);
      if (args == null) {
        continue;
      }
      made++;
      String what = tool + " " + args;
      Map<String, Integer> tally = outcomes.computeIfAbsent(tool, k -> new TreeMap<>());
      try {
        // Two entries are another tool with an option: move_type with package, extract_variable as.
        String real =
            switch (tool) {
              case "move_type_package" -> "move_type";
              case "extract_variable_constant" -> "extract_variable";
              default -> tool;
            };
        Tools.call(Tools.named(real), project, args);
      } catch (Refusal r) {
        String reason = r.getMessage().replaceAll("'[^']*'|\"[^\"]*\"|\\b\\w*Zq\\b", "_");
        reason = reason.substring(0, Math.min(60, reason.length())).replaceAll("\\s+", " ");
        tally.merge("refused: " + reason, 1, Integer::sum);
        if (r.getMessage().contains("compile errors")) {
          // The gate caught what the tool wrote: a precondition the tool may be missing.
          System.out.println("stress gated: " + what + ": " + r.getMessage().replace('\n', ' '));
        }
        continue;
      } catch (Exception | Error e) {
        tally.merge("failed", 1, Integer::sum);
        failures.add(what + ": " + e);
        restore(project, texts);
        continue;
      }
      tally.merge("applied", 1, Integer::sum);
      Map<String, Integer> full = TypeCheck.uncached(project);
      List<String> added = TypeCheck.introduced(first, full);
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
        args.put("end_line", unit.getLineNumber(b.getStartPosition() + b.getLength() - 1));
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
      case "extract_struct" -> {
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
        args.put("struct", "PairZq");
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

  /** The project's top-level class names, and the simple names its classes extend. */
  private record Hierarchy(Set<String> classes, Set<String> extended) {
    static Hierarchy of(Collection<String> texts) {
      Set<String> classes = new HashSet<>();
      Set<String> extended = new HashSet<>();
      for (String text : texts) {
        for (Object t : Jdt.parseSyntax(text).types()) {
          if (t instanceof TypeDeclaration c && !c.isInterface()) {
            classes.add(c.getName().getIdentifier());
            if (c.getSuperclassType() != null) {
              extended.add(simple(c.getSuperclassType()));
            }
          }
        }
      }
      return new Hierarchy(classes, extended);
    }

    /** Whether {@code c} extends a class of the project. */
    boolean superclassIn(TypeDeclaration c) {
      return c.getSuperclassType() != null && classes.contains(simple(c.getSuperclassType()));
    }

    private static String simple(Type type) {
      String name = type.toString().replaceAll("<.*", "");
      return name.substring(name.lastIndexOf('.') + 1);
    }
  }
}
