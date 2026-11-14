package com.example;

import java.util.Iterator;

class Printer {
  void print(Iterator<Shape> it) {
    if (it.next() instanceof Circle c) {
      System.out.println("circle");
    } else if (it.next() instanceof Square q) {
      System.out.println("square");
    }
  }
}
