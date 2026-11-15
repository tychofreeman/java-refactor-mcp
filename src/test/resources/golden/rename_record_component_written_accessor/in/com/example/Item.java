package com.example;

record Item(String subKey, int count) {
  @Override
  public String subKey() {
    return subKey.strip();
  }
}
