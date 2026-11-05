package com.example;

public class Cart {
  private int total;

  void add(int price) {
    total += price;
  }

  int count() {
    return 1;
  }
}
