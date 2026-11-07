package com.example;

public class Cart {
  private int size;

  private int limit;

  Cart(int size) {
    this.size = size;
  }

  int size() {
    return size;
  }

  boolean empty() {
    return size == 0;
  }
}
