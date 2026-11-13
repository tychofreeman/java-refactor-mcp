package com.example;

import java.util.HashMap;
import java.util.Map;

public class Repo<K extends Comparable<K>, V> {
  protected final Map<String, V> entries = new HashMap<>();

  public V first() {
    return entries.values().iterator().next();
  }

  public void save(K key, V value) {
    entries.put(key.toString(), value);
  }
}
