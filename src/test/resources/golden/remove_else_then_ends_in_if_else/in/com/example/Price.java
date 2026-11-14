package com.example;

public class Price {
  public int total(int a) {
    if (a == 0) {
      if (a > 1) {
        return 2;
      } else {
        return 1;
      }
    } else {
      a = a * 2;
      return a;
    }
  }
}
