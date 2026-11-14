package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;

class SignatureChangeTest {

  private static final String CALLS =
      """
      class A {
        void f() {
          g();
          g(a, b);
        }
      }
      """;

  private static String inserted(MethodInvocation call, int at) {
    int open = CALLS.indexOf('(', call.getStartPosition()) + 1;
    TextEdit edit = SignatureChange.insertAt(call.arguments(), open, at, "x");
    String text = TextEdit.apply(CALLS, List.of(edit), 0);
    int start = call.getStartPosition();
    return text.substring(start, text.indexOf(';', start));
  }

  @Test
  void anItemGoesAloneInAnEmptyListBeforeAnElementOrAfterTheLast() {
    List<MethodInvocation> calls = Nodes.all(Jdt.parseSyntax(CALLS), MethodInvocation.class);
    assertEquals("g(x)", inserted(calls.get(0), 0));
    assertEquals("g(x, a, b)", inserted(calls.get(1), 0));
    assertEquals("g(a, x, b)", inserted(calls.get(1), 1));
    assertEquals("g(a, b, x)", inserted(calls.get(1), 2));
  }
}
