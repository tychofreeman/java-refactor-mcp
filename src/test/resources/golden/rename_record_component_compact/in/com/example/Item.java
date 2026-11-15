package com.example;

record Item(String subKey, int count) {
  Item {
    subKey = subKey.strip();
  }
}
