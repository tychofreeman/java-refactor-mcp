package com.example;

class Calc {
  int first(int x, int y) {
    Op pick = (b, a) -> a;
    return pick.apply(y, x);
  }
}
