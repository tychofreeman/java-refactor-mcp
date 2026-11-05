package com.example;

public class A {
  int f(int x, int k) {
    int v = x;
    int sum = 0;
    for (int i = 0; i < k; i++) {
      sum += v;
      x++;
    }
    return sum;
  }
}
