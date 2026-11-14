package com.example;

class Printer {
  void print(Shape s) {
    switch (s) {
      case Circle c -> {
        Circle circle = c;
        Runnable r =
            new Runnable() {
              public void run() {
                System.out.println(circle);
              }
            };
        r.run();
      }
      default -> System.out.println("other");
    }
  }
}
