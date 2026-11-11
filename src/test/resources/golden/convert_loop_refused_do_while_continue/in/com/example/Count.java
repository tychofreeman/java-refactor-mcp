package com.example;

public class Count {
  void count() {
    int i = 0;
    do {
      i++;
      if (i % 2 == 0) {
        continue;
      }
      System.out.println(i);
    } while (i < 9);
  }
}
