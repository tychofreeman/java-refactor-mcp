package com.example;

public final class Reservation {
  private int days;

  private final String book;

  private final String member;

  public Reservation(String book, String member) {
    this.book = book;
    this.member = member;
  }
}
