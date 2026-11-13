package com.example;

public class Repo<K, V> extends Cached<K, V> {
  public V load(K key) {
    last = cache.get(key);
    return last;
  }
}
