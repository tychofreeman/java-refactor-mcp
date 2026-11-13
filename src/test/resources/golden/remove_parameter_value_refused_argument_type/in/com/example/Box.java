package com.example;

class Box {
  static final long LIMIT = 100;

  boolean fits(long width, long height, long area) {
    return area <= LIMIT;
  }

  boolean check(int w, int h) {
    return fits(w, h, w * h);
  }
}
