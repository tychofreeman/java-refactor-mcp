package com.example;

import java.util.List;

public class Index<K, V extends List<K>> implements Listing<K, V> {
  private final V items;

  public Index(V values) {
    this.items = values;
  }

  public V values() {
    return items;
  }

  public K first() {
    return items.get(0);
  }
}
