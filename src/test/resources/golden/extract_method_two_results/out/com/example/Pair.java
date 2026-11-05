package com.example;

class Pair {
  int run(int a) {
    BothResult bothResult = both(a);
    int x = bothResult.x();
    int y = bothResult.y();
    return x + y;
  }

  private BothResult both(int a) {
    int x = a + 1;
    int y = a * 2;
    return new BothResult(x, y);
  }

  private record BothResult(int x, int y) {}
}
