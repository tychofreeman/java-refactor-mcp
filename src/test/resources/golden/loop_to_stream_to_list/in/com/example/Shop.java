package com.example;

import java.util.ArrayList;
import java.util.List;

public class Shop {
  void show(List<Item> items) {
    List<String> labels = new ArrayList<>();
    for (Item item : items) {
      if (item.price() > 10) {
        String label = item.name() + ": " + item.price();
        labels.add(label.trim());
      }
    }
    labels.forEach(System.out::println);
  }

  record Item(String name, int price) {}
}
