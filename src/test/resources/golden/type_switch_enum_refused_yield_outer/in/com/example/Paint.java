package com.example;

class Paint {
  int weight(Color c, int k) {
    return switch (k) {
      case 0 -> {
        switch (c) {
          case RED -> {
            yield 5;
          }
          default -> System.out.println(c);
        }
        yield 0;
      }
      default -> 1;
    };
  }
}
