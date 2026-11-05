package com.example;

public class Price {
  int f(Item p) {
    try {
      return p.cost;
    } catch (RuntimeException e) {
      return -1;
    }
  }
}

class Item {
  int cost;
}
