package com.example;

public class Cart {
  int count;

  int total() {
    return count * 2;
  }

  int twice() {
    return total() * 2;
  }
}
