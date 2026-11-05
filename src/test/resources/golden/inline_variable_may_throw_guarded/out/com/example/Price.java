package com.example;

public class Price {
  int f(Item p, boolean c) {
    if (c) {
      return p.cost;
    }
    return 0;
  }
}

class Item {
  int cost;
}
