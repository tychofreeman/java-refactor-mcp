package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.BiFunction;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Random edits to a real project's declarations, each compared, with the edit and again after the
 * file is restored, between the narrowed count ({@link TypeCheck#errors}) and a full one ({@link
 * TypeCheck#uncached}): a difference is a change the narrowing (roadmap #56) misses. Opt-in: {@code
 * -Djrefactor.stress.root=<dir>}, with {@code jrefactor.stress.edits} (default 30), {@code
 * jrefactor.stress.seed} (printed, to rerun) and {@code jrefactor.stress.counts=true} to print the
 * counts without failing. Every file is restored, also on failure.
 */
class NarrowCheckStressTest {

  /**
   * One kind of edit: the new text of a file for a declaration in it, or null if it does not fit.
   */
  private record Kind(String name, BiFunction<String, BodyDeclaration, String> edit) {}

  private static final List<Kind> KINDS =
      List.of(
          new Kind(
              "rename method",
              (t, d) ->
                  d instanceof MethodDeclaration m && !m.isConstructor()
                      ? rename(t, m.getName())
                      : null),
          new Kind(
              "rename field",
              (t, d) ->
                  d instanceof FieldDeclaration f
                      ? rename(t, ((VariableDeclarationFragment) f.fragments().get(0)).getName())
                      : null),
          new Kind(
              "rename type",
              (t, d) -> d instanceof AbstractTypeDeclaration a ? rename(t, a.getName()) : null),
          new Kind("add a parameter", NarrowCheckStressTest::addParameter),
          new Kind("make private", (t, d) -> modifier(t, d, "private")),
          new Kind("make static", (t, d) -> modifier(t, d, "static")),
          new Kind(
              "return Object",
              (t, d) ->
                  d instanceof MethodDeclaration m
                          && m.getReturnType2() != null
                          && !(m.getReturnType2() instanceof PrimitiveType p
                              && p.getPrimitiveTypeCode() == PrimitiveType.VOID)
                      ? replace(t, m.getReturnType2(), "Object")
                      : null),
          new Kind(
              "field of Object",
              (t, d) -> d instanceof FieldDeclaration f ? replace(t, f.getType(), "Object") : null),
          new Kind("drop supertypes", NarrowCheckStressTest::dropSupertypes),
          new Kind(
              "delete method",
              (t, d) ->
                  d instanceof MethodDeclaration
                      ? t.substring(0, d.getStartPosition()) + t.substring(end(d))
                      : null),
          new Kind("body only", NarrowCheckStressTest::bodyOnly));

  @Test
  @EnabledIfSystemProperty(named = "jrefactor.stress.root", matches = ".+")
  void narrowedCountsAreFullCounts() throws Exception {
    SourceProject project =
        SourceProject.load(Path.of(System.getProperty("jrefactor.stress.root")));
    int edits = Integer.getInteger("jrefactor.stress.edits", 30);
    long seed = Long.getLong("jrefactor.stress.seed", System.nanoTime());
    boolean countsOnly = Boolean.getBoolean("jrefactor.stress.counts");
    Random random = new Random(seed);
    List<Path> files = project.javaFiles();
    TypeCheck.errors(project);
    System.out.printf("stress: %d files, %d edits, seed %d%n", files.size(), edits, seed);

    List<String> misses = new ArrayList<>();
    int made = 0;
    long rechecked = 0;
    for (int tries = 0; made < edits && tries < edits * 20; tries++) {
      Path file = files.get(random.nextInt(files.size()));
      String text = Files.readString(file);
      List<BodyDeclaration> decls = declarations(Jdt.parseSyntax(text));
      if (decls.isEmpty()) {
        continue;
      }
      BodyDeclaration d = decls.get(random.nextInt(decls.size()));
      Kind kind = KINDS.get(random.nextInt(KINDS.size()));
      String edited = kind.edit().apply(text, d);
      if (edited == null || edited.equals(text)) {
        continue;
      }
      made++;
      String what = kind.name() + " in " + project.relative(file) + " at " + d.getStartPosition();
      try {
        Files.writeString(file, edited);
        Map<String, Integer> narrowed = TypeCheck.errors(project);
        rechecked += TypeCheck.lastParsed(project).size();
        compare(what, narrowed, TypeCheck.uncached(project), misses);
      } finally {
        Files.writeString(file, text);
      }
      compare(what + ", restored", TypeCheck.errors(project), TypeCheck.uncached(project), misses);
    }
    System.out.printf(
        "stress: %d edits, %.1f files re-checked per edit, %d misses%n",
        made, made == 0 ? 0.0 : (double) rechecked / made, misses.size());
    misses.forEach(m -> System.out.println("stress miss: " + m));
    if (!countsOnly) {
      assertEquals(List.of(), misses, "seed " + seed);
    }
  }

  private static void compare(
      String what, Map<String, Integer> narrowed, Map<String, Integer> full, List<String> misses) {
    if (narrowed.equals(full)) {
      return;
    }
    TreeSet<String> keys = new TreeSet<>(narrowed.keySet());
    keys.addAll(full.keySet());
    keys.removeIf(k -> narrowed.getOrDefault(k, 0).equals(full.getOrDefault(k, 0)));
    List<String> diff = new ArrayList<>();
    for (String k : keys) {
      diff.add(
          k
              + " (narrowed "
              + narrowed.getOrDefault(k, 0)
              + ", full "
              + full.getOrDefault(k, 0)
              + ")");
    }
    misses.add(what + ": " + String.join("; ", diff));
  }

  /** The declarations other files can see: types, and their members, nothing inside bodies. */
  private static List<BodyDeclaration> declarations(CompilationUnit unit) {
    List<BodyDeclaration> out = new ArrayList<>();
    List<AbstractTypeDeclaration> types = new ArrayList<>();
    for (Object o : unit.types()) {
      types.add((AbstractTypeDeclaration) o);
    }
    while (!types.isEmpty()) {
      AbstractTypeDeclaration t = types.remove(0);
      out.add(t);
      for (Object o : t.bodyDeclarations()) {
        if (o instanceof AbstractTypeDeclaration inner) {
          types.add(inner);
        } else if (o instanceof MethodDeclaration || o instanceof FieldDeclaration) {
          out.add((BodyDeclaration) o);
        }
      }
    }
    return out;
  }

  private static String rename(String text, ASTNode name) {
    return text.substring(0, end(name)) + "Zq" + text.substring(end(name));
  }

  private static String replace(String text, ASTNode node, String with) {
    return text.substring(0, node.getStartPosition()) + with + text.substring(end(node));
  }

  private static String addParameter(String text, BodyDeclaration d) {
    if (!(d instanceof MethodDeclaration m)) {
      return null;
    }
    List<?> params = m.parameters();
    if (params.isEmpty()) {
      int open = text.indexOf('(', end(m.getName()));
      return text.substring(0, open + 1) + "int zq" + text.substring(open + 1);
    }
    SingleVariableDeclaration last = (SingleVariableDeclaration) params.get(params.size() - 1);
    if (last.isVarargs()) {
      return null;
    }
    return text.substring(0, end(last)) + ", int zq" + text.substring(end(last));
  }

  /** {@code d} with modifier {@code word} added, replacing an access modifier for "private". */
  private static String modifier(String text, BodyDeclaration d, String word) {
    if (d instanceof TypeDeclaration t && t.getParent() instanceof CompilationUnit) {
      return null; // a top-level type cannot be private or static
    }
    for (Object o : d.modifiers()) {
      if (o instanceof Modifier m) {
        if (m.getKeyword().toString().equals(word)) {
          return null;
        }
        if (word.equals("private") && (m.isPublic() || m.isProtected())) {
          return replace(text, m, "private");
        }
      }
    }
    int at = d.getJavadoc() == null ? d.getStartPosition() : end(d.getJavadoc());
    while (Character.isWhitespace(text.charAt(at))) {
      at++;
    }
    return text.substring(0, at) + word + " " + text.substring(at);
  }

  private static String dropSupertypes(String text, BodyDeclaration d) {
    if (!(d instanceof TypeDeclaration t)
        || (t.getSuperclassType() == null && t.superInterfaceTypes().isEmpty())) {
      return null;
    }
    List<?> params = t.typeParameters();
    int from =
        params.isEmpty()
            ? end(t.getName())
            : text.indexOf('>', end((ASTNode) params.get(params.size() - 1))) + 1;
    int to = text.indexOf('{', from);
    return text.substring(0, from) + " " + text.substring(to);
  }

  private static String bodyOnly(String text, BodyDeclaration d) {
    if (!(d instanceof MethodDeclaration m) || m.getBody() == null) {
      return null;
    }
    int at = m.getBody().getStartPosition() + 1;
    return text.substring(0, at) + " int zqq = 0; " + text.substring(at);
  }

  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }
}
