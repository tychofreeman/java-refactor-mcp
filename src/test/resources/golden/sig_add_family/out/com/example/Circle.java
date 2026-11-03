package com.example;

class Circle implements Shape {
  final double r;

  Circle(double r) {
    this.r = r;
  }

  @Override
  public double area(double scale) {
    return Math.PI * r * r;
  }
}
