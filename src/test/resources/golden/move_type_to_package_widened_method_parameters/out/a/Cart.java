package a;

import b.Shop;

public class Cart {
  int total(Shop s) {
    return s.price(2, "x");
  }
}
