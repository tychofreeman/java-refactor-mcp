package com.example;

public class Repo<K extends Comparable<K>, V> extends Stored<V> {
  public void save(K key, V value) {
    entries.put(key.toString(), value);
  }
}
