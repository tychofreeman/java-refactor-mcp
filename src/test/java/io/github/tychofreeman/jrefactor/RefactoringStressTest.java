package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
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
          "extract_method");

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
    for (int tries = 0; made < calls && tries < calls * 20; tries++) {
      Path file = files.get(random.nextInt(files.size()));
      String tool = TOOLS.get(random.nextInt(TOOLS.size()));
      String text = texts.get(file);
      Map<String, Object> args =
          target(tool, project.relative(file), Jdt.parseSyntax(text), random);
      if (args == null) {
        continue;
      }
      made++;
      String what = tool + " " + args;
      Map<String, Integer> tally = outcomes.computeIfAbsent(tool, k -> new TreeMap<>());
      try {
        Tools.call(Tools.named(tool), project, args);
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
      String tool, String rel, CompilationUnit unit, Random random) {
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
}
