package com.example;

public class Count {
  void count() {
    int i = 0;
    outer:
    while (true) {
      i++;
      if (i >= 3) break;
    }
    System.out.println(i);
  }
}
