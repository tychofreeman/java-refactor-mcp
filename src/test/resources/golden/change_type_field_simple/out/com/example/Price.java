package com.example;

public class Price {
  private long total;

  public void add(int amount) {
    total = total + amount;
  }

  public long get() {
    return total;
  }
}
