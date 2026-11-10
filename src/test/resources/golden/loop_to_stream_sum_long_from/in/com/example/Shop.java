package com.example;

import java.util.List;

public class Shop {
  long weight(List<Item> items, long crate) {
    long weight = crate;
    for (Item item : items) {
      if (item.heavy()) {
        weight += item.grams();
      }
    }
    return weight;
  }

  record Item(int grams, boolean heavy) {}
}
