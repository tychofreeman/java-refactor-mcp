package com.example;

public class Count {
  int first(int k) {
    return switch (k) {
      case 0 -> {
        for (int i = 0; i < 3; i++) {
          yield i;
        }
        yield -1;
      }
      default -> k;
    };
  }
}
