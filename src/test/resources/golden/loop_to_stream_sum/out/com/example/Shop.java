package com.example;

import java.util.List;

public class Shop {
  int total(List<Item> items) {
    int total = items.stream().mapToInt(item -> item.price()).sum();
    return total;
  }

  record Item(String name, int price) {}
}
