package com.example;

class Printer {
  void print(Shape s) {
    if (s instanceof Circle c) {
      System.out.println(c.r());
    } else if (s instanceof Square q) {
      System.out.println(q.side());
    }
  }
}
