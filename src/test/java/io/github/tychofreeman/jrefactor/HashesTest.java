package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HashesTest {

  @Test
  void theHashOfPartsIsTheHashOfTheirConcatenation() {
    String abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    assertEquals(abc, Hashes.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    assertEquals(
        abc,
        Hashes.sha256Hex(
            "a".getBytes(StandardCharsets.UTF_8), "bc".getBytes(StandardCharsets.UTF_8)));
  }
}
