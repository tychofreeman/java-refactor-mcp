package com.example;

class Printer {
  void print(Shape s) {
    switch (s) {
      case Circle c -> System.out.println(c.r());
      case Square q -> {
        double side = q.side();
        System.out.println(side);
      }
    }
  }
}
