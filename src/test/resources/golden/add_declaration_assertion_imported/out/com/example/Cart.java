package com.example;

class Cart {
  private final int sum;

  Cart(int start) {
    sum = start;
  }

  int total() {
    return sum;
  }
}
