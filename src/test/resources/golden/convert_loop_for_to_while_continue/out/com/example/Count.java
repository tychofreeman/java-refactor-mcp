package com.example;

public class Count {
  void odd() {
    int i = 0;
    while (i < 10) {
      if (i % 2 == 0) {
        i++;
        continue;
      }
      if (i > 7) {
        i++;
        continue;
      }
      for (int k = 0; k < i; k++) {
        if (k == 1) continue;
        System.out.println(k);
      }
      System.out.println(i);
      i++;
    }
  }
}
