package com.example;

public final class Reservation {
  private final String book;

  private final String member;

  public Reservation(String book, String member) {
    this.book = book.strip();
    this.member = member;
  }
}
