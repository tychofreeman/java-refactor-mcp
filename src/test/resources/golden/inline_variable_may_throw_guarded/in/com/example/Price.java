package com.example;

public class Price {
  int f(Item p, boolean c) {
    int a = p.cost;
    if (c) {
      return a;
    }
    return 0;
  }
}

class Item {
  int cost;
}
