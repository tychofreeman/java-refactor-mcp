package com.example;

import java.util.HashMap;
import java.util.Map;

public class Repo<K extends Comparable<K>, V> {
  private final Map<K, V> entries = new HashMap<>();

  public V find(K key) {
    return entries.get(key);
  }

  public void save(K key, V value) {
    entries.put(key, value);
  }

  public V first() {
    return entries.values().iterator().next();
  }

  public int size() {
    return entries.size();
  }
}
