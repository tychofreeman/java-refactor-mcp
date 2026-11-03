package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
