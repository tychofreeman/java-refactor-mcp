package com.example;

public interface Finder<K extends Comparable<K>, V> {
  V find(K key);
}
