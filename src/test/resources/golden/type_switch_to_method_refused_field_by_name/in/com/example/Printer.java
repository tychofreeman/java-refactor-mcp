package com.example;

class Printer {
  double radius = 10;

  void print(Shape s) {
    switch (s) {
      case Circle c -> {
        for (int radius = 0; radius < 1; radius++) {}
        System.out.println(radius);
      }
      case Square q -> System.out.println(1);
    }
  }
}
