package com.example;

public class Price {
  private int total;

  public void add(int amount) {
    total = total + amount;
  }

  public long get() {
    return total;
  }
}
