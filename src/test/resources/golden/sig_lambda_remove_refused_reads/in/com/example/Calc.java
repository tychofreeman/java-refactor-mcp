package com.example;

class Calc {
  int first(int x, int y) {
    Op pick = (a, b) -> b;
    return pick.apply(x, y);
  }
}
