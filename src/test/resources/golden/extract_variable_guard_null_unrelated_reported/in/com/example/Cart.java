package com.example;

public class Cart {
  boolean paid(boolean ready, Item p) {
    return ready && p.cost > 0;
  }
}

class Item {
  int cost;
}
