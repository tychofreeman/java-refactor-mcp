package com.example;

public class A {
  int count;

  void bump() {
    count++;
  }

  int f() {
    int y = count;
    bump();
    return y;
  }
}
