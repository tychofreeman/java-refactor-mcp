package com.example;

public class Name implements Comparable<Name> {
  private final String text;

  public Name(String text) {
    this.text = text;
  }

  @Override
  public int compareTo(Name other) {
    return text.compareTo(other.text);
  }
}
