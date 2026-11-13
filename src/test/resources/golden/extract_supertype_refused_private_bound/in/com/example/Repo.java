package com.example;

import java.util.HashMap;
import java.util.Map;

public class Repo<K extends Repo.Key, V> {
  private interface Key {
    String text();
  }

  private final Map<String, V> entries = new HashMap<>();

  public V find(K key) {
    return entries.get(key.text());
  }
}
