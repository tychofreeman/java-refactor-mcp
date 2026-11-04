package com.example;

public class Use {
  private int n;

  int next() {
    return n++;
  }

  int all() {
    return Lib.sub(next(), next());
  }
}
