package com.example;

class Shop {
  /** The most items in a cart. */
  private static final int MAX = 10;

  int cap(int n) {
    return Math.min(n, MAX);
  }

  static int twice() {
    return Shop.MAX * 2;
  }
}
