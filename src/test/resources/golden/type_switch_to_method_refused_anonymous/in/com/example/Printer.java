package com.example;

class Printer {
  void print(Shape s) {
    switch (s) {
      case Circle c -> {
        Runnable r =
            new Runnable() {
              public void run() {
                System.out.println(c);
              }
            };
        r.run();
      }
      default -> System.out.println("other");
    }
  }
}
