package com.example;

import java.util.List;

class Printer {
  void print(List<Shape> shapes) {
    outer:
    for (Shape s : shapes) {
      switch (s) {
        case Circle c -> {
          for (int i = 0; i < 3; i++) {
            if (c.r() > i) {
              break outer;
            }
          }
          System.out.println(c.r());
        }
        case Square q -> System.out.println(q.side());
      }
    }
  }
}
