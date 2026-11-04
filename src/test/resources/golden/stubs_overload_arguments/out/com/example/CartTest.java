package com.example;

class CartTest {
  void empty() {
    Cart cart = new Cart();
  }

  void totals() {
    Cart cart = new Cart();
    int quantity = 3;
    cart.add("apple", quantity);
  }
}
