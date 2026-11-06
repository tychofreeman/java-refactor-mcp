package com.example;

class Geometry {
  double area(Shape s, double k) {
    return switch (s) {
      case Circle c -> Math.PI * c.r() * c.r();
      case Square q -> q.side() * k;
    };
  }
}
