package com.example;

public class Price {
  int total;

  int f(Item p) {
    int a = p.cost;
    total++;
    return a;
  }
}

class Item {
  int cost;
}
