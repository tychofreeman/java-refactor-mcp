package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

  @Test
  void aCountIsPluralUnlessItIsOne() {
    assertEquals("0 occurrences", Names.plural(0, "occurrence"));
    assertEquals("1 occurrence", Names.plural(1, "occurrence"));
    assertEquals("2 occurrences", Names.plural(2, "occurrence"));
  }

  @Test
  void aMethodBindingIsLabelledErasedWithItsEnclosingTypesOrAsSourceWritesIt() throws IOException {
    IMethodBinding add = method(parse(), "add").resolveBinding();

    assertEquals("Shop.Cart.add(List, int)", Names.methodLabel(add));
    assertEquals("add(List<String>, int)", Names.signature(add));
    assertEquals("Cart.add(List<String>, int)", Names.sourceLabel(add));
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
}
