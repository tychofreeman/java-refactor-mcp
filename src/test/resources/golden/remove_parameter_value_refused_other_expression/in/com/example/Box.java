package com.example;

class Box {
  static final int LIMIT = 100;

  boolean fits(int width, int height, int area) {
    return area <= LIMIT;
  }

  boolean check(int w, int h) {
    return fits(w, h, w * h) && fits(w, h, w + h);
  }
}
