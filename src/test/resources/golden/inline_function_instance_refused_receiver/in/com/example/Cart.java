package com.example;

public class Cart {
  int count;

  int total() {
    return count * 2;
  }

  static Cart make() {
    return new Cart();
  }

  static int f() {
    return make().total();
  }
}
