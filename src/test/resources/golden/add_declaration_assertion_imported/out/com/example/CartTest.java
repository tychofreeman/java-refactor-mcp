package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CartTest {
  @Test
  void total() {
    assertEquals(5, new Cart(5).total());
  }
}
