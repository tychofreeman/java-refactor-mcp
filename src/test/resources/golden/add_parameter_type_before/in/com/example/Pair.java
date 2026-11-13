package com.example;

public class Pair<A> {
  private final A first;

  public Pair(A first) {
    this.first = first;
  }

  Pair<A> same() {
    return this;
  }
}
