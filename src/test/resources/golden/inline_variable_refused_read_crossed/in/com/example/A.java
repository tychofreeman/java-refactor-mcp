package com.example;

public class A {
  int count;

  int next() {
    return count++;
  }

  int f() {
    int n = next();
    int y = count;
    return n + y;
  }
}
