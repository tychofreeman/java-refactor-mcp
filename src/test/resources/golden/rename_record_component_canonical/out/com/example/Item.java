package com.example;

record Item(String key, int count) {
  Item(String key, int count) {
    this.key = key.strip();
    this.count = count;
  }

  Item(String subKey) {
    this(subKey, 1);
  }
}
