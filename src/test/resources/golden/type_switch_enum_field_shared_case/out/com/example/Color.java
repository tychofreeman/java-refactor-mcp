package com.example;

public enum Color {
  RED("r", Paint.HEAVY),
  GREEN("g", 2),
  BLUE("b", 2);

  private final String code;

  private final int weight;

  Color(String code, int weight) {
    this.code = code;
    this.weight = weight;
  }

  public int weight() {
    return weight;
  }
}
