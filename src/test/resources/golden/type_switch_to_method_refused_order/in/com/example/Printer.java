package com.example;

class Printer {
  void print(Shape s) {
    if (s instanceof Polygon p) {
      System.out.println("polygon");
    } else if (s instanceof Square q) {
      System.out.println("square");
    }
  }
}
