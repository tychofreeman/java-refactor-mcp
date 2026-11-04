package com.example;

public class Price {
  public int total(String a) {
    if (a.isEmpty()) {
      return 1;
    } else {
      return a.length();
    }
  }
}
