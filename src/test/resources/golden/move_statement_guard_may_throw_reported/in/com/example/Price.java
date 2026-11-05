package com.example;

public class Price {
  int f(Item p) {
    if (p == null) {
      return 0;
    }
    int x = p.cost;
    return x;
  }
}

class Item {
  int cost;
}
