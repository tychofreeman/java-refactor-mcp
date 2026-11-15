package com.example;

record Item(String subKey) {
  String label() {
    String key = "x";
    return key + subKey;
  }
}
