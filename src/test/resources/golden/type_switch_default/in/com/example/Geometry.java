package com.example;

class Geometry {
  double area(Shape s) {
    return switch (s) {
      case Circle c -> Math.PI * c.r() * c.r();
      default -> 0.0;
    };
  }
}
