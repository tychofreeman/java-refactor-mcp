package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IndentShiftTest {
  @Test
  void find() {
    String text = "class A {\n  void a() {\n\n    run();\n  }\n}\n";
    IndentShift s = IndentShift.find(text, "    void a() {\n\n      run();\n");
    assertEquals(new IndentShift(10, text.indexOf("  }"), -2, true), s);
  }

  @Test
  void twoMatchesAreNone() {
    assertNull(IndentShift.find("  a();\n  a();\n", "    a();"));
  }

  @Test
  void unevenShiftsAreNone() {
    assertNull(IndentShift.find("  a();\n  b();\n", "    a();\n      b();"));
  }

  @Test
  void spacingWithinALineMustMatch() {
    assertNull(IndentShift.find("  int x = 1;\n", "    int x  = 1;"));
  }

  @Test
  void shiftLeavesTheFirstLineWhenTheMatchStartsInsideOne() {
    assertEquals("a\n    b\n\n    c", IndentShift.shift("a\n  b\n\n  c", 2, false));
    assertEquals("a\nb", IndentShift.shift("  a\n b", -2, true));
  }
}
