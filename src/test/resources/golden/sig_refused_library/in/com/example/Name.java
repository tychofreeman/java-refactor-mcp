package com.example;

class Name implements Comparable<Name> {
  final String text = "";

  @Override
  public int compareTo(Name o) {
    return text.compareTo(o.text);
  }
}
