package com.example;

public class Renamer {
  private final StringBuilder log;

  public Renamer(StringBuilder log) {
    this.log = log;
  }

  boolean move(String from, String to) {
    return true;
  }

  void run(String from, String to) {
    if (move(from, to)) {
      log.append(from).append(" -> ").append(to);
    }
  }
}
