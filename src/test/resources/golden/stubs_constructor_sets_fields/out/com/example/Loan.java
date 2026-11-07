package com.example;

public class Loan {
  private String title;
  private String author;
  private int days;
  private final int max = 5;

  Loan() {}

  Loan(String author, int days) {
    this.author = author;
    this.days = days;
  }
}
