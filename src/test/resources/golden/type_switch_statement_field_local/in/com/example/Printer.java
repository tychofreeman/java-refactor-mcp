package com.example;

class Printer {
  void print(Shape s) {
    switch (s) {
      case Circle c -> {
        double radius = 2;
        System.out.println(c.radius * radius);
      }
      case Square q -> System.out.println(1);
    }
  }
}
