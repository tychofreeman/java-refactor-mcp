package com.example;

record Pair<K>(K key, String label) {
  Pair(K key, String label) {
    this.key = key;
    this.label = label.strip();
  }
}
