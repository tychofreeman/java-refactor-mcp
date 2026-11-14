package com.example;

public class Cart {
  boolean paid(boolean ready, Item p) {
    int cost = p.cost;
    return ready && cost > 0;
  }
}

class Item {
  int cost;
}
