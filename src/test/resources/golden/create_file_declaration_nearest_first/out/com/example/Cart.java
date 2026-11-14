package com.example;

class Cart extends Base implements Priced {
  @Override
  public int price() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public String name() {
    throw new UnsupportedOperationException("not implemented");
  }
}
