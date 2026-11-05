package com.example;

public class Shape {
  int width = 2;

  static int area(Shape s, int k) {
    return s.width * k;
  }

  int twice() {
    return area(this, 2);
  }
}
