package com.example;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class CartTest {
  @Test
  void total() {
    Cart cart = new Cart(0);
    int total = cart.total();
    fail("write the assertion");
  }

  @Test
  void add() {
    Cart cart = new Cart(0);
    cart.add(0);
    fail("write the assertion");
  }
}
