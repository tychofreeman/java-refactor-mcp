package com.example;

public class Count {
  void odd() {
    for (int i = 0; i < 10; i++) {
      if (i % 2 == 0) {
        continue;
      }
      if (i > 7) continue;
      for (int k = 0; k < i; k++) {
        if (k == 1) continue;
        System.out.println(k);
      }
      System.out.println(i);
    }
  }
}
