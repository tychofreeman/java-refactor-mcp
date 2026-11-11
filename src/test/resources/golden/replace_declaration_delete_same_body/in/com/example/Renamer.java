package com.example;

public class Renamer {
  boolean carryOut(String r) {
    print(r);
    return move(r);
  }

  void print(String r) {
    System.out.println(r);
  }

  boolean move(String r) {
    return false;
  }
}
