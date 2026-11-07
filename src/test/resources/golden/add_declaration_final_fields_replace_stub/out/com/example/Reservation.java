package com.example;

public final class Reservation {
  private int days;

  public Reservation(String book, String member) {
    this.book = book;
    this.member = member;
  }

  private final String book;

  private final String member;
}
