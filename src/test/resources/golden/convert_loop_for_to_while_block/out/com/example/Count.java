package com.example;

public class Count {
  void count() {
    {
      int i = 0, j = 9;
      outer:
      while (i < j) {
        if (i == 3) break outer;
        System.out.println(i + j);
        i++;
        j--;
      }
    }
    for (int i = 0; i < 2; i++) {
      System.out.println(-i);
    }
  }
}
