package com.example;

public class Count {
  void count() {
    for (int i = 0; i < 9; ) {
      i++;
      if (i % 3 == 0) {
        continue;
      }
      System.out.println(i);
      i++;
    }
  }
}
