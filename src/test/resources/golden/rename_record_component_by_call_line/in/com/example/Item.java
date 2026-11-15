package com.example;

/**
 * An item.
 *
 * @param subKey its key
 * @param count how many
 */
record Item(String subKey, int count) {
  String label() {
    return subKey + count;
  }

  boolean same(Item other) {
    return this.subKey.equals(other.subKey());
  }
}
