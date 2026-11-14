package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamesTest {

  @TempDir Path dir;

  private static final String SHOP =
      """
      package p;

      import java.util.List;

      class Shop {
        static class Cart {
          void add(List<String> names, int n) {}
        }

        void run() {
          class Local {
            void go() {}
          }
        }
      }
      """;

  private static final String CART =
      """
      package p;

      import java.util.List;
      import java.util.Map;

      class Shop {
        static class Cart {
          void add(List<String> names, int n) {}

          void add(String name) {}

          <T> T raise(T t) {
            return t;
          }

          <T extends Throwable> T raise(T t) {
            return t;
          }

          void log(String format, Object... args) {}

          void put(Map<String, List<Integer>> m) {}

          void each(Map.Entry<String, ? extends Number> e) {}

          void copy(java.util.Set<String> s) {}
        }
      }
      """;

  @Test
  void aCountIsPluralUnlessItIsOne() {
    assertEquals("0 occurrences", Names.plural(0, "occurrence"));
    assertEquals("1 occurrence", Names.plural(1, "occurrence"));
    assertEquals("2 occurrences", Names.plural(2, "occurrence"));
  }

  @Test
  void aMethodBindingIsLabelledWithItsEnclosingTypesAndItsTypesAsSourceWritesThem()
      throws IOException {
    IMethodBinding add = method(parse(), "add").resolveBinding();

    assertEquals("Shop.Cart.add(List<String>, int)", Names.methodLabel(add));
  }

  @Test
  void aDeclarationIsLabelledByTheTypesAroundIt() throws IOException {
    CompilationUnit unit = parse();

    assertEquals("Shop.Cart", Names.typeLabelAt(method(unit, "add")));
    assertEquals("Shop.Local", Names.typeLabelAt(method(unit, "go")));
    assertEquals("Shop", Names.typeLabelAt(method(unit, "run")));
  }

  private CompilationUnit parse() throws IOException {
    Path file = dir.resolve("p/Shop.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, SHOP);
    return Jdt.parse(SourceProject.load(dir), file, SHOP);
  }

  private static MethodDeclaration method(CompilationUnit unit, String name) {
    List<MethodDeclaration> all = Nodes.all(unit, MethodDeclaration.class);
    return all.stream().filter(m -> m.getName().getIdentifier().equals(name)).findFirst().get();
  }

  @Test
  void aListNamesItsItemsWithAndBeforeTheLast() {
    assertEquals("a", Names.listed(List.of("a")));
    assertEquals("a and b", Names.listed(List.of("a", "b")));
    assertEquals("a, b and c", Names.listed(List.of("a", "b", "c")));
  }

  @Test
  void aSimpleNameIsWhatFollowsTheLastDot() {
    assertEquals("Map", Names.simpleName("java.util.Map"));
    assertEquals("Map", Names.simpleName("Map"));
  }

  @Test
  void aUnitsPackageIsItsDeclarationOrEmpty() {
    assertEquals("a.b", Names.packageOf(Jdt.parseSyntax("package a.b; class C {}")));
    assertEquals("", Names.packageOf(Jdt.parseSyntax("class C {}")));
  }

  @Test
  void everyMethodLabelNamesItsMethodBackInEveryTool() throws IOException {
    Path file = dir.resolve("p/Shop.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, CART);
    CompilationUnit unit = Jdt.parse(SourceProject.load(dir), file, CART);
    AbstractTypeDeclaration cart = Declarations.typePath(unit, new String[] {"Shop", "Cart"});
    List<String> labels = new ArrayList<>();
    for (MethodDeclaration m : Nodes.all(cart, MethodDeclaration.class)) {
      IMethodBinding b = m.resolveBinding();
      String label = Names.methodLabel(b);
      labels.add(label);
      if (!m.getName().getIdentifier().equals("copy")) {
        assertEquals(label, "Shop.Cart." + Declarations.methodLabel(m));
      }
      assertSame(m, Declarations.find(unit, label, "p/Shop.java").decl(), label);
      Qualified q = Qualified.parse(label);
      assertSame(m, MemberMove.member(cart, q.type(), q.member(), "p/Shop.java", "pull_up"), label);
      for (MethodDeclaration other : Nodes.all(cart, MethodDeclaration.class)) {
        assertEquals(
            other == m,
            other.getName().getIdentifier().equals(q.name())
                && Names.takes(other.resolveBinding(), q.params()),
            label + " against " + Names.methodLabel(other.resolveBinding()));
      }
    }
    assertEquals(
        List.of(
            "Shop.Cart.add(List<String>, int)",
            "Shop.Cart.add(String)",
            "Shop.Cart.raise(Object)",
            "Shop.Cart.raise(Throwable)",
            "Shop.Cart.log(String, Object...)",
            "Shop.Cart.put(Map<String, List<Integer>>)",
            "Shop.Cart.each(Map.Entry<String, ? extends Number>)",
            "Shop.Cart.copy(Set<String>)"),
        labels);
    assertEquals(
        "copy(java.util.Set<String>)",
        Declarations.methodLabel(method(unit, "copy")),
        "a declaration's label writes a qualified type as source does; both are taken back");
  }
}
