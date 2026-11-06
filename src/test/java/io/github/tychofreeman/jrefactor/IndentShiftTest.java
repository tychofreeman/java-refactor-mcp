package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndentShiftTest {
  @Test
  void aLooseMatchSaysHowFarNewMoves() {
    String text = "class A {\n  void a() {\n\n    run();\n  }\n}\n";
    List<IndentShift> found = IndentShift.locate(text, "    void a() {\n\n      run();\n");
    assertEquals(List.of(new IndentShift(10, text.indexOf("  }"), -2, true)), found);
  }

  @Test
  void eachLooseMatchIsFound() {
    assertEquals(2, IndentShift.locate("  a();\n  a();\n", "    a();").size());
  }

  @Test
  void spacingWithinALineMayDiffer() {
    assertEquals(1, IndentShift.locate("  int x = 1;\n", "int x  = 1;").size());
  }

  @Test
  void aLineBreakMustStayALineBreak() {
    assertEquals(0, IndentShift.locate("  a(); b();\n", "a();\nb();").size());
  }

  @Test
  void shiftLeavesTheFirstLineWhenTheMatchStartsInsideOne() {
    assertEquals("a\n    b\n\n    c", IndentShift.shift("a\n  b\n\n  c", 2, false));
    assertEquals("a\nb", IndentShift.shift("  a\n b", -2, true));
  }
}
