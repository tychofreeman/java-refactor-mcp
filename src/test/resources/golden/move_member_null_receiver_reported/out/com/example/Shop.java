package com.example;

public class Shop {
  int bill(Price p, int a) {
    return Fees.charge(p, a);
  }
}
