package com.example;

public class Price {
  int total;

  int f(Item p) {
    total++;
    return p.cost;
  }
}

class Item {
  int cost;
}
