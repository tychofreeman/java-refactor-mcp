package com.example;

record Item(String key, int count) {
  @Override
  public String key() {
    return key.strip();
  }
}
