package com.example;

class Geometry {
  int sides(Shape s) {
    return switch (s) {
      case Circle c -> 0;
      case Unit u -> 4;
    };
  }
}
