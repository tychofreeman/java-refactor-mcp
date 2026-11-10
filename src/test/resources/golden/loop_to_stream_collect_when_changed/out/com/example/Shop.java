package com.example;

import java.util.List;
import java.util.stream.Collectors;

public class Shop {
  List<String> names(List<Item> items) {
    List<String> names = items.stream().map(item -> item.name()).collect(Collectors.toList());
    names.add("total");
    return names;
  }

  record Item(String name, int price) {}
}
