package com.example;

public class Shape {
  int width = 2;

  int area(int k) {
    return this.width * k;
  }

  int twice() {
    return area(2);
  }
}
