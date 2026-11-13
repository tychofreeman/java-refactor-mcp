package com.example;

public enum Color {
  RED("r"),
  GREEN("g", 2);

  Color(String code) {}

  Color(String code, int n) {}
}
