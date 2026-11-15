package com.example;

import java.util.List;
import java.util.function.Function;

class Use {
  String first(Item item) {
    String subKey = item.key();
    return subKey;
  }

  List<String> keys(List<Item> items) {
    Function<Item, String> f = Item::key;
    return items.stream().map(f).toList();
  }
}
