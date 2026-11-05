package com.example;

public class Price {
  int f(Item p) {
    int a = p.cost;
    try {
      return a;
    } catch (RuntimeException e) {
      return -1;
    }
  }
}

class Item {
  int cost;
}
