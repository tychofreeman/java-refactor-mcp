package com.example;

class Square implements Shape {
  final double s;

  Square(double s) {
    this.s = s;
  }

  @Override
  public double area() {
    return s * s;
  }
}
