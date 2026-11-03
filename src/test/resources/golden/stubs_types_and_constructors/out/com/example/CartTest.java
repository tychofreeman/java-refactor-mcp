package com.example;

class CartTest {
  void empty() {
    Cart cart = new Cart();
  }

  void baskets() {
    Cart cart = new Cart(2);
    Basket basket = new Basket(cart);
    long count = basket.count();
    check(count);
  }

  void check(long count) {
    throw new UnsupportedOperationException("not implemented");
  }
}
