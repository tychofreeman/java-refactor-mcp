package com.example;

import java.util.List;

public class Shelf {
  private final List<? extends Item> items;

  Shelf(List<Item> items) {
    this.items = items;
  }

  String first() {
    return items.get(0).name();
  }

  int size() {
    return items.size();
  }
}
