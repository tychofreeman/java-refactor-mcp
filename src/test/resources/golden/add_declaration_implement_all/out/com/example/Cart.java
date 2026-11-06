package com.example;

class Cart implements Priced {
  int count;

  @Override
  public int price() {
    throw new UnsupportedOperationException("not implemented");
  }
}
