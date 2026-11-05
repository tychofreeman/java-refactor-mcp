package com.example;

public class A {
  int count;

  void bump() {
    count++;
  }

  int f() {
    int v = count;
    bump();
    return v;
  }
}
