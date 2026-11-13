package com.example;

class Box {
  static final int LIMIT = 100;

  boolean fits(int width, int height, int area) {
    return area <= LIMIT;
  }

  boolean check(int w, int h) {
    int scale = 2;
    return fits(w, h, w * scale);
  }
}
