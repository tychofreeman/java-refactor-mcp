package com.example;

public class Counter {
  static int next(int a) {
    return (a)++;
  }

  int use(int x) {
    return next(x) + x;
  }
}
