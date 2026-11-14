package com.example;

public class Count {
  int first(int k) {
    return switch (k) {
      case 0 -> {
        int i = 0;
        while (i < 3) {
          yield i;
        }
        yield -1;
      }
      default -> k;
    };
  }
}
