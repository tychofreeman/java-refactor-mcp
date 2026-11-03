package com.example;

public class Totals {
  /**
   * Adds the tax.
   *
   * @param total the amount before tax
   * @return the total with tax
   */
  int withTax(int total) {
    return total + total / 10;
  }
}
