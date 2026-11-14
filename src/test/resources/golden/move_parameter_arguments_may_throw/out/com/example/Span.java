package com.example;

class Span {
  static int width(int end, int start) {
    return end - start;
  }

  static int of(int[] bounds, int scale) {
    return width(bounds[1] / scale, bounds[0]);
  }
}
