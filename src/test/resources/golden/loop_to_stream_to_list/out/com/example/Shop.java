package com.example;

import java.util.List;

public class Shop {
  void show(List<Item> items) {
    List<String> labels = items.stream()
        .filter(item -> item.price() > 10)
        .map(item -> item.name() + ": " + item.price())
        .map(label -> label.trim())
        .toList();
    labels.forEach(System.out::println);
  }

  record Item(String name, int price) {}
}
