package com.example;

import java.util.Objects;

public class Cart {
  boolean paid(Item p) {
    return Objects.nonNull(p) && p.cost > 0;
  }
}

class Item {
  int cost;
}
