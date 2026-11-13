package com.example;

public class Pair<A, B> {
  private final A first;

  public Pair(A first) {
    this.first = first;
  }

  public A first() {
    return first;
  }

  public Pair<A, B> swap() {
    return new Pair<>(first);
  }
}
