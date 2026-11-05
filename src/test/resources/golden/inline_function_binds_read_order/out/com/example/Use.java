package com.example;

public class Use {
  int all() {
    int a = Counter.bump();
    int b = Counter.count;
    return b + a;
  }
}
