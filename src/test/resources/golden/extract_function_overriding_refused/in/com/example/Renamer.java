package com.example;

public class Renamer {
  boolean carryOut(String r) {
    print(r);
    return move(r);
  }

  void print(String r) {
    System.out.println(r);
  }

  boolean twice(String r) {
    return move(r) && move(r);
  }

  boolean move(String r) {
    return true;
  }
}
