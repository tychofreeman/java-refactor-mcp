package com.example;

interface Priced {
  int total(int a);
}

public class Price {
  public int total(int a) {
    return a;
  }
}
