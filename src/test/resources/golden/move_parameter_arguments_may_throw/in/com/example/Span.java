package com.example;

class Span {
  static int width(int start, int end) {
    return end - start;
  }

  static int of(int[] bounds, int scale) {
    return width(bounds[0], bounds[1] / scale);
  }
}
