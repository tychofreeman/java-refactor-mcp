package com.example;

public class Cart {
  int count = 1;

  int total() {
    return count * 2;
  }

  int f() {
    int count = 10;
    return total() + count;
  }
}
