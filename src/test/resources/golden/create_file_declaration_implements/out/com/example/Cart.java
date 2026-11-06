package com.example;

final class Cart implements Priced {
  @Override
  public int price() {
    throw new UnsupportedOperationException("not implemented");
  }
}
