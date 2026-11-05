package com.example;

public class Shop {
  int bill(Price p, int a) {
    return Fees.charge(java.util.Objects.requireNonNull(p), a);
  }
}
