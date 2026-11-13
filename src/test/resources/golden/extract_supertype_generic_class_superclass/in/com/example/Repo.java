package com.example;

import java.util.HashMap;
import java.util.Map;

public class Repo<K, V> extends Store<V> {
  protected final Map<K, V> cache = new HashMap<>();

  public void evict(K key) {
    cache.remove(key);
  }

  public V load(K key) {
    last = cache.get(key);
    return last;
  }
}
