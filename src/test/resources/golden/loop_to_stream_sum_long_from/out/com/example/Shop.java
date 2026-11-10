package com.example;

import java.util.List;

public class Shop {
  long weight(List<Item> items, long crate) {
    long weight = crate + items.stream()
        .filter(item -> item.heavy())
        .mapToLong(item -> item.grams())
        .sum();
    return weight;
  }

  record Item(int grams, boolean heavy) {}
}
