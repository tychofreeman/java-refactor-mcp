package com.example;

public class Pair<K extends Comparable<K>, A> {
  private final A first;

  public Pair(A first) {
    this.first = first;
  }

  Pair<K, A> same() {
    return this;
  }
}
