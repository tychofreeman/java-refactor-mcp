package com.example;

class Geometry {
  double area(Shape s) {
    return switch (s) {
      case Shape.Circle c -> Math.PI * c.r() * c.r();
      case Shape.Square q -> q.side() * q.side();
    };
  }
}
