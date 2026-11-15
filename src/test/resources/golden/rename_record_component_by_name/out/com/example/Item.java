package com.example;

/**
 * An item.
 *
 * @param key its key
 * @param count how many
 */
record Item(String key, int count) {
  String label() {
    return key + count;
  }

  boolean same(Item other) {
    return this.key.equals(other.key());
  }
}
