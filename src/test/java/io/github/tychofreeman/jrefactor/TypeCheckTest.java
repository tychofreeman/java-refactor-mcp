package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeCheckTest {

  @Test
  void aCountFollowsEveryChangeOnDisk(@TempDir Path root) throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    String good = "package com.example;\n\nclass A {\n  int x = 1;\n}\n";
    Files.writeString(file, good);
    SourceProject project = SourceProject.of(root);

    assertEquals(Map.of(), TypeCheck.errors(project));
    Files.writeString(file, good.replace("1", "\"one\""));
    assertEquals(
        Map.of("com/example/A.java | Type mismatch: cannot convert from String to int", 1),
        TypeCheck.errors(project));
    Files.writeString(file, good);
    assertEquals(Map.of(), TypeCheck.errors(project));
  }

  @Test
  void aCountIsReusedWhileNothingChanged(@TempDir Path root) throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "package com.example;\n\nclass A {}\n");
    SourceProject project = SourceProject.of(root);

    TypeCheck.errors(project);
    int parsed = TypeCheck.parses(project);
    TypeCheck.errors(project);
    assertEquals(parsed, TypeCheck.parses(project));
  }

  /** The command line runs one call per process; a saved count serves the next one's check. */
  @Test
  void aSavedCountServesANewProcess(@TempDir Path root, @TempDir Path cache) throws Exception {
    Path file = root.resolve("com/example/A.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "package com.example;\n\nclass A {\n  int x = \"one\";\n}\n");
    SourceProject first = SourceProject.of(root);
    Map<String, Integer> counted = TypeCheck.errors(first);
    Path saved = cache.resolve("count.json");
    TypeCheck.save(first, saved);

    SourceProject second = SourceProject.of(root);
    TypeCheck.load(second, saved);
    assertEquals(counted, TypeCheck.errors(second));
    assertEquals(0, TypeCheck.parses(second));

    Files.writeString(file, "package com.example;\n\nclass A {}\n");
    SourceProject third = SourceProject.of(root);
    TypeCheck.load(third, saved);
    assertEquals(Map.of(), TypeCheck.errors(third));
    assertEquals(1, TypeCheck.parses(third));
  }

  private static void write(Path root, String name, String body) throws Exception {
    Path file = root.resolve("com/example/" + name + ".java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "package com.example;\n\n" + body + "\n");
  }

  private static Set<String> lastParsed(SourceProject project) {
    List<Path> parsed = TypeCheck.lastParsed(project);
    Set<String> out = new TreeSet<>();
    for (Path p : parsed) {
      out.add(p.getFileName().toString());
    }
    return out;
  }

  /** A: m(); B calls it and returns an A; D reaches m() through B only; C is unrelated. */
  private static SourceProject fourFiles(Path root) throws Exception {
    write(root, "A", "class A {\n  int m() {\n    return 1;\n  }\n}");
    write(
        root,
        "B",
        "class B {\n  A a() {\n    return new A();\n  }\n\n  int n() {\n    return a().m();\n  }\n}");
    write(root, "C", "class C {\n  int c() {\n    return 3;\n  }\n}");
    write(root, "D", "class D {\n  int p(B b) {\n    return b.a().m();\n  }\n}");
    SourceProject project = SourceProject.of(root);
    assertEquals(Map.of(), TypeCheck.errors(project));
    return project;
  }

  @Test
  void aBodyChangeRechecksOnlyItsFile(@TempDir Path root) throws Exception {
    SourceProject project = fourFiles(root);
    write(root, "A", "class A {\n  int m() {\n    return 2; // changed\n  }\n}");

    assertEquals(Map.of(), TypeCheck.errors(project));
    assertEquals(Set.of("A.java"), lastParsed(project));
  }

  @Test
  void aChangedHeaderRechecksTheFilesThatNameIt(@TempDir Path root) throws Exception {
    SourceProject project = fourFiles(root);
    write(root, "A", "class A {\n  int k() {\n    return 1;\n  }\n}");

    Map<String, Integer> errors = TypeCheck.errors(project);
    assertEquals(Set.of("A.java", "B.java", "D.java"), lastParsed(project));
    assertEquals(TypeCheck.uncached(project), errors);
    assertEquals(2, errors.size(), errors.toString());
  }

  @Test
  void aNewFileRechecksTheFilesThatNamedItsType(@TempDir Path root) throws Exception {
    SourceProject project = fourFiles(root);
    write(root, "C", "class C {\n  int c() {\n    return new E().e();\n  }\n}");
    assertTrue(!TypeCheck.errors(project).isEmpty());
    write(root, "E", "class E {\n  int e() {\n    return 5;\n  }\n}");

    assertEquals(Map.of(), TypeCheck.errors(project));
    assertEquals(Set.of("C.java", "E.java"), lastParsed(project));
  }

  /** From the stress test on Commons Math: a member change a subclass inherits. */
  @Test
  void aChangedMemberRechecksSubclassesThatDoNotNameIt(@TempDir Path root) throws Exception {
    write(root, "I", "interface I {\n  boolean full();\n}");
    write(
        root,
        "Base",
        "abstract class Base implements I {\n  public boolean full() {\n    return true;\n  }\n}");
    write(root, "Sub", "class Sub extends Base {}");
    SourceProject project = SourceProject.of(root);
    assertEquals(Map.of(), TypeCheck.errors(project));
    write(
        root,
        "Base",
        "abstract class Base implements I {\n  public static boolean full() {\n    return true;\n  }\n}");

    assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project));
  }

  /** From the stress test on Commons Math: a supertype change seen through a subclass only. */
  @Test
  void aChangedSupertypeRechecksUsersOfItsSubclasses(@TempDir Path root) throws Exception {
    write(root, "Bad", "class Bad extends RuntimeException {}");
    write(root, "Worse", "class Worse extends Bad {}");
    write(root, "User", "class User {\n  void u() {\n    throw new Worse();\n  }\n}");
    SourceProject project = SourceProject.of(root);
    assertEquals(Map.of(), TypeCheck.errors(project));
    write(root, "Bad", "class Bad {}");

    assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project));
  }
}
