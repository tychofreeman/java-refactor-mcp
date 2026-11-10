package com.example;

import java.util.List;

public class Shop {
  int total(List<Item> items) {
    int total = 0;
    for (Item item : items) {
      total += item.price();
    }
    return total;
  }

  record Item(String name, int price) {}
}
