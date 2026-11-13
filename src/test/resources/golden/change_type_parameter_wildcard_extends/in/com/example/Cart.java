package com.example;

import java.util.ArrayList;
import java.util.List;

public class Cart {
  private final List<Item> lines = new ArrayList<>();

  void addAll(List<Item> items) {
    for (Item i : items) {
      lines.add(i);
    }
  }
}
