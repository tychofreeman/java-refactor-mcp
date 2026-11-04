package com.example;

class Calc {
  int first(int x, int y) {
    Op pick = (a, b, c) -> a;
    Op typed = (int a, int b, int c) -> b;
    return pick.apply(x, y, 0) + typed.apply(x, y, 0);
  }
}
