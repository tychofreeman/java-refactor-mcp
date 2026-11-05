package com.example;

public class Use {
  private int n;

  int next() {
    return n++;
  }

  int all(boolean c) {
    int x = next();
    return c ? x : 0;
  }
}
