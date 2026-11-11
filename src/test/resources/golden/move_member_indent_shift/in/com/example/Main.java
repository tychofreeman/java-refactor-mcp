package com.example;

public class Main {
  public static void main(String[] args) {
    System.out.println(carryOut("a"));
  }

  static String carryOut(String r) {
    if (r.isEmpty()) {
      return "none";
    }
    return r + "!";
  }
}
