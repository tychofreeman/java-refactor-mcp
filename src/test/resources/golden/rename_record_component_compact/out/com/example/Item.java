package com.example;

record Item(String key, int count) {
  Item {
    key = key.strip();
  }
}
