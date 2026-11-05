package com.example;

public class Price {
  int total;

  int f(Item p) {
    total++;
    int x = p.cost;
    return x;
  }
}

class Item {
  int cost;
}
