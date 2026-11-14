package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class LinesTest {

  @Test
  void aRangeIsOneLineOrManyWithItsEnds() {
    assertEquals("line 3", Lines.range(3, 3));
    assertEquals("lines 3-5", Lines.range(3, 5));
  }

  @Test
  void aListOfLinesNamesEachOne() {
    assertEquals("line 4", Lines.list(List.of(4)));
    assertEquals("lines 4, 7, 9", Lines.list(List.of(4, 7, 9)));
  }

  @Test
  void theNumberOfAnOffsetIsCountedFromOne() {
    assertEquals(3, Lines.number("a\nb\nc", 4));
  }

  @Test
  void aLineStartsAndEndsAroundAnOffset() {
    assertEquals(3, Lines.start("ab\ncd", 4));
    assertEquals(3, Lines.end("ab\ncd", 0));
    assertEquals(5, Lines.end("ab\ncd", 3));
  }

  @Test
  void aLineAndColumnAreAnOffsetWithinTheLine() {
    assertEquals(4, Lines.offsetOf("ab\ncd\n", 2, 2));
    assertEquals(5, Lines.offsetOf("ab\ncd\n", 2, 3));
    assertEquals(
        "column 4 is past the end of line 2.",
        assertThrows(Refusal.class, () -> Lines.offsetOf("ab\ncd\n", 2, 4)).getMessage());
    assertEquals(
        "line 4 is past the end of the file.",
        assertThrows(Refusal.class, () -> Lines.offsetOf("ab\ncd", 4, 1)).getMessage());
    assertEquals(
        "line and column start at 1; got line 0, column 1.",
        assertThrows(Refusal.class, () -> Lines.offsetOf("ab", 0, 1)).getMessage());
  }
}
