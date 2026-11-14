package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IndentTest {

  @Test
  void aLinesWidthCountsItsSpacesAndTabs() {
    assertEquals(0, Indent.width("int x;"));
    assertEquals(3, Indent.width(" \t int x;"));
  }

  @Test
  void theStepIsTheSmallestIndentOfACodeLine() {
    assertEquals(2, Indent.step("class A {\n  /**\n   * Doc.\n   */\n  int x;\n}"));
    assertEquals(0, Indent.step("class A {}"));
    assertEquals(4, Indent.step("class A {}", 4));
    assertEquals(2, Indent.step("class A {\n  int x;\n}", 4));
  }

  @Test
  void theCommonIndentIsReplaced() {
    assertEquals("  a\n    b\n\n  c", Indent.reindent("      a\n        b\n   \n      c", "  "));
    assertEquals("", Indent.reindent("   ", "  "));
  }

  @Test
  void aMovedMemberTakesTheNewStep() {
    String moved = "void f() {\n    if (x) {\n        y();\n    }\n}";
    assertEquals("void f() {\n    if (x) {\n      y();\n    }\n  }", Indent.restep(moved, "  ", 2));
  }

  @Test
  void aCommentsContinuationLinesAreNoStep() {
    String moved =
        "/**\n     * Doc,\n     * two lines.\n     */\n    void f() {\n        y();\n    }";
    assertEquals(
        "/**\n   * Doc,\n   * two lines.\n   */\n  void f() {\n    y();\n  }",
        Indent.restep(moved, "  ", 2));
  }
}
