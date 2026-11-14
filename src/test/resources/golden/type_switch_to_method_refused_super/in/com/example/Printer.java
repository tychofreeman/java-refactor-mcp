package com.example;

class Printer extends Base {
  void print(Shape s) {
    switch (s) {
      case Circle c -> System.out.println(super.describe());
      case Square q -> System.out.println("square");
    }
  }
}
