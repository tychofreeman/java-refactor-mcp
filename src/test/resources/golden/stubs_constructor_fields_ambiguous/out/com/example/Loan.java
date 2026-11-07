package com.example;

public class Loan {
  private String title;
  private String author;
  private int days;
  private final int max = 5;

  Loan() {}

  Loan(String string, int i) {
    throw new UnsupportedOperationException("not implemented");
  }
}
