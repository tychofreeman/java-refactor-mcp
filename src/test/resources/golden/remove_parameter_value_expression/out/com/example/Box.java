package com.example;

class Box {
  static final int LIMIT = 100;

  /**
   * Whether a box fits.
   *
   * @param width its width
   * @param height its height
   */
  boolean fits(int width, int height) {
    int area = width * height;
    return area <= LIMIT;
  }

  boolean check(int w, int h) {
    return fits(w, h + 1) && fits(w, h);
  }
}
