package com.example;

class Cart {
  private int sum;

  Cart(int start) {
    sum = start;
  }

  void add(int price) {
    sum += price;
  }

  int total() {
    return sum;
  }
}
