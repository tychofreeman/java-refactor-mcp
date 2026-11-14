package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {

  @Test
  void aCountIsPluralUnlessItIsOne() {
    assertEquals("0 occurrences", Names.plural(0, "occurrence"));
    assertEquals("1 occurrence", Names.plural(1, "occurrence"));
    assertEquals("2 occurrences", Names.plural(2, "occurrence"));
  }
}
