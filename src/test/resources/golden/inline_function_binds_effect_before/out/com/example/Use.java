package com.example;

public class Use {
  int all() {
    int x = Counter.count;
    return Counter.bump() + x;
  }
}
