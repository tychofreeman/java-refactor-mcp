package com.example;

public class Cart {
  private int size;

  Cart(int size) {
    this.size = size;
  }

  Cart() {
    this(0);
  }

  int size() {
    return size;
  }
}
