package com.example;

record Pair(String key, String label) {
  Pair(String key, String label) {
    this.key = key;
    this.label = label.strip();
  }
}
