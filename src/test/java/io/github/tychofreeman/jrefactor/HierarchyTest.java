package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HierarchyTest {

  private static final String SOURCE =
      """
      package p;

      interface I {
        void m();
      }

      interface J extends I {}

      interface K extends I {}

      abstract class S implements J {}

      class C extends S implements K {
        public void m() {}

        void locals() {
          class A {}
          class B {}
        }
      }
      """;

  private static CompilationUnit parse(Path root) throws Exception {
    Path file = root.resolve("p/C.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, SOURCE);
    return Jdt.parse(SourceProject.of(root), file, SOURCE);
  }

  private static ITypeBinding type(CompilationUnit unit, String name) {
    return Nodes.all(unit, TypeDeclaration.class).stream()
        .filter(t -> t.getName().getIdentifier().equals(name))
        .findFirst()
        .orElseThrow()
        .resolveBinding();
  }

  private static List<String> names(List<ITypeBinding> types) {
    return types.stream().map(ITypeBinding::getName).toList();
  }

  /** Breadth-first, nearest first, an interface reached twice (a diamond) listed once. */
  @Test
  void supertypesAreNearestFirstAndEachOnce(@TempDir Path root) throws Exception {
    ITypeBinding c = type(parse(root), "C");

    assertEquals(List.of("S", "K", "Object", "J", "I"), names(Hierarchy.supertypes(c, false)));
    assertEquals(List.of("C", "S", "K", "Object", "J", "I"), names(Hierarchy.supertypes(c, true)));
  }

  /** A method that a diamond reaches twice is overridden once. */
  @Test
  void overriddenListsEachMethodOnce(@TempDir Path root) throws Exception {
    IMethodBinding m =
        Arrays.stream(type(parse(root), "C").getDeclaredMethods())
            .filter(d -> d.getName().equals("m"))
            .findFirst()
            .orElseThrow();

    List<IMethodBinding> overridden = Hierarchy.overridden(m);

    assertEquals(
        List.of("I"), overridden.stream().map(o -> o.getDeclaringClass().getName()).toList());
  }

  /**
   * By key, not by qualified name: two local classes both have an empty qualified name. Every
   * reference type is an Object, an interface too.
   */
  @Test
  void subtypesCompareByKey(@TempDir Path root) throws Exception {
    CompilationUnit unit = parse(root);
    ITypeBinding object = type(unit, "S").getSuperclass();

    assertFalse(Hierarchy.isSubtype(type(unit, "A"), type(unit, "B")));
    assertTrue(Hierarchy.isSubtype(type(unit, "C"), type(unit, "I")));
    assertTrue(Hierarchy.isSubtype(type(unit, "K"), object));
    assertTrue(Hierarchy.isA(type(unit, "K"), "java.lang.Object"));
    assertTrue(Hierarchy.isA(type(unit, "C"), "p.J"));
    assertFalse(Hierarchy.isA(type(unit, "S"), "p.K"));
  }
}
