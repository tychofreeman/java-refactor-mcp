package com.example;

import java.util.ArrayList;
import java.util.List;

public class Shop {
  List<String> names(List<Item> items) {
    List<String> names = new ArrayList<>();
    for (Item item : items) {
      names.add(item.name());
    }
    names.add("total");
    return names;
  }

  record Item(String name, int price) {}
}
