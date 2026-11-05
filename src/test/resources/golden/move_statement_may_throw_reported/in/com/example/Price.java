package com.example;

public class Price {
  int total;

  int f(Item p) {
    int x = p.cost;
    total++;
    return x;
  }
}

class Item {
  int cost;
}
