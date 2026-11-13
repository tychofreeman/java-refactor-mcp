package com.example;

class Box {
  static final int LIMIT = 100;
  private int n;

  boolean fits(int width, int height, int area) {
    return area <= LIMIT;
  }

  int next() {
    return ++n;
  }

  boolean check(int h) {
    return fits(next(), h, next() * h);
  }
}
