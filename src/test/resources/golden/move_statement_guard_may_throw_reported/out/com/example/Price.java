package com.example;

public class Price {
  int f(Item p) {
    int x = p.cost;
    if (p == null) {
      return 0;
    }
    return x;
  }
}

class Item {
  int cost;
}
