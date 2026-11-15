package com.example;

record Item(String subKey) {
  String key() {
    return subKey;
  }
}
