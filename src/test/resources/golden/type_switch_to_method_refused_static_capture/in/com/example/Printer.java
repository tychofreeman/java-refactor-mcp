package com.example;

class Printer {
  static final double K = 1;

  void print(Shape s) {
    switch (s) {
      case Circle c -> System.out.println(K);
      case Square q -> System.out.println(2);
    }
  }
}
