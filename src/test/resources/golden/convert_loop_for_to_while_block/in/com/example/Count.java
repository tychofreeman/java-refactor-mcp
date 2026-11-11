package com.example;

public class Count {
  void count() {
    outer:
    for (int i = 0, j = 9; i < j; i++, j--) {
      if (i == 3) break outer;
      System.out.println(i + j);
    }
    for (int i = 0; i < 2; i++) {
      System.out.println(-i);
    }
  }
}
