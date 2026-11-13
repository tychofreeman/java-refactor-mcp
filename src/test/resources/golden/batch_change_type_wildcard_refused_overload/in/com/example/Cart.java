package com.example;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Cart {
  private final List<Item> lines = new ArrayList<>();

  void addAll(List<Item> items) {
    for (Item i : items) {
      lines.add(i);
    }
  }

  void addAll(Collection<? extends Item> items) {
    lines.addAll(items);
    lines.add(new Item());
  }
}
