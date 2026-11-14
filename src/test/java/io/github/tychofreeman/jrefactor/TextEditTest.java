package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextEditTest {

  @Test
  void editsApplyFromTheEndInAnyOrder() {
    assertEquals(
        "a-X-cY",
        TextEdit.apply(
            "a-b-c", List.of(new TextEdit(5, 5, "Y"), new TextEdit(2, 3, "X")), 0));
  }

  @Test
  void anInsertAtAReplacementsStartGoesBeforeIt() {
    assertEquals(
        "aIX",
        TextEdit.apply("abc", List.of(new TextEdit(1, 1, "I"), new TextEdit(1, 3, "X")), 0));
  }

  @Test
  void aWindowCountsOffsetsFromItsStart() {
    assertEquals("bZ", TextEdit.apply("bc", List.of(new TextEdit(11, 12, "Z")), 10));
  }

  @Test
  void overlappingEditsAreAToolBug() {
    assertThrows(
        IllegalStateException.class,
        () ->
            TextEdit.apply(
                "abcdef", List.of(new TextEdit(1, 4, "X"), new TextEdit(3, 5, "Y")), 0));
    assertThrows(
        IllegalStateException.class,
        () ->
            TextEdit.apply(
                "abcdef", List.of(new TextEdit(1, 4, "X"), new TextEdit(2, 2, "Y")), 0));
  }
}
