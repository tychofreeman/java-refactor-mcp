package com.example;

record Item(String subKey, int count) {
  Item(String subKey, int count) {
    this.subKey = subKey.strip();
    this.count = count;
  }

  Item(String subKey) {
    this(subKey, 1);
  }
}
