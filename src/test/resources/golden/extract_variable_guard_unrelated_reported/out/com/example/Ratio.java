package com.example;

public class Ratio {
  int of(boolean on, int n, int d) {
    int q = n / d;
    return on ? q : 0;
  }
}
