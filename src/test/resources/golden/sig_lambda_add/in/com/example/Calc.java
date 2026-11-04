package com.example;

class Calc {
  int first(int x, int y) {
    Op pick = (a, b) -> a;
    Op typed = (int a, int b) -> b;
    return pick.apply(x, y) + typed.apply(x, y);
  }
}
