package com.example;

public class Cart {
  int count = 1;

  int total() {
    return count * 2;
  }

  Runnable later(StringBuilder out) {
    return new Runnable() {
      int count = 5;

      public void run() {
        out.append(total());
      }
    };
  }
}
