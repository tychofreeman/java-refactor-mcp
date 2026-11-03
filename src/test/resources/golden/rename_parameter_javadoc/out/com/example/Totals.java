package com.example;

public class Totals {
  /**
   * Adds the tax.
   *
   * @param amount the amount before tax
   * @return the total with tax
   */
  int withTax(int amount) {
    return amount + amount / 10;
  }
}
