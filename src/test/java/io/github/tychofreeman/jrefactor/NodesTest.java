package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NodesTest {

  private static final CompilationUnit UNIT =
      Jdt.parseSyntax(
          """
          class C {
            int m() {
              Runnable r = () -> { return; };
              return ((1 + 2));
            }
          }
          """);

  private static ReturnStatement outerReturn() {
    return Nodes.all(UNIT, ReturnStatement.class).stream()
        .filter(r -> r.getExpression() != null)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void anAncestorIsAboveTheNodeAndAnAncestorOrSelfMayBeIt() {
    ReturnStatement r = outerReturn();
    assertSame(r.getParent(), Nodes.ancestor(r, Block.class));
    assertSame(r, Nodes.ancestorOrSelf(r, Statement.class));
    assertSame(r.getParent(), Nodes.ancestor(r, Statement.class));
    assertNull(Nodes.ancestor(r, LambdaExpression.class));
    assertNull(Nodes.ancestorOrSelf(null, Statement.class));
  }

  @Test
  void enclosingStopsAtALambda() {
    ReturnStatement inLambda = Nodes.all(UNIT, ReturnStatement.class).get(0);
    assertNull(Nodes.enclosing(inLambda, MethodDeclaration.class));
    assertSame(
        Nodes.all(UNIT, MethodDeclaration.class).get(0),
        Nodes.enclosing(outerReturn(), MethodDeclaration.class));
  }

  @Test
  void theTypeAroundIsTheNamedOrAnonymousClassAboveTheNode(@TempDir Path root) throws Exception {
    String source =
        """
        package p;

        class C {
          class D {}

          Object o = new Object() { int x = 1; };
        }
        """;
    Path file = root.resolve("p/C.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, source);
    CompilationUnit unit = Jdt.parse(SourceProject.of(root), file, source);
    TypeDeclaration d = Nodes.all(unit, TypeDeclaration.class).get(1);
    assertEquals("C", Nodes.typeAround(d).getName());
    NumberLiteral one = Nodes.all(unit, NumberLiteral.class).get(0);
    assertEquals(true, Nodes.typeAround(one).isAnonymous());
    assertNull(Nodes.typeAround(unit));
  }

  @Test
  void everyParenthesisAroundAnExpressionIsStripped() {
    Expression e = Nodes.unparenthesized(outerReturn().getExpression());
    assertEquals(InfixExpression.class, e.getClass());
    NumberLiteral one = Nodes.all(UNIT, NumberLiteral.class).get(0);
    assertSame(one, Nodes.unparenthesized(one));
  }

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
