package com.example;

class Printer {
  void print(Shape s) {
    switch (s) {
      case Circle c -> {
        class Half {
          double of(double x) {
            return x / 2;
          }
        }
        System.out.println(new Half().of(c.r()));
      }
      case Square q -> System.out.println(q.side());
    }
  }
}
