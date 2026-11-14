package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;

class NodesTest {

  private static final String NAMES =
      "class A {\n  void f(java.util.List<String> names) {\n    names.forEach(\n        n -> {\n          System.out.println(n);\n        });\n    names\n        .stream()\n        .count();\n    String.join(\", \", \"aaaaaaaaaaaaaaaaaaaa\", \"bbbbbbbbbbbbbbbbbbbbbbbb\", \"cccccccccc\");\n  }\n}\n";

  @Test
  void showsANodeAsItsSourceOnOneLine() {
    CompilationUnit unit = Jdt.parseSyntax(NAMES);
    List<MethodInvocation> calls = Nodes.all(unit, MethodInvocation.class);
    assertEquals("names.forEach(n -> { System.out.println(n); })", Nodes.show(calls.get(0)));
    assertEquals("names.stream().count()", Nodes.show(calls.get(2)));
  }

  @Test
  void cutsALongNodeToSixtyCharacters() {
    CompilationUnit unit = Jdt.parseSyntax(NAMES);
    String shown = Nodes.show(Nodes.all(unit, MethodInvocation.class).get(4));
    assertEquals(60, shown.length());
    assertTrue(shown.startsWith("String.join(\", \", \"aaaa"), shown);
    assertTrue(shown.endsWith("..."), shown);
  }
}
