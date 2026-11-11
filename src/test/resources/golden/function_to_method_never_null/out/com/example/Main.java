package com.example;

public class Main {
  public static void main(String[] args) {
    Renamer renamer = new Renamer(System.out, System.err);
    renamer.carryOut("a");
    (new Renamer(System.out, System.err)).carryOut("b");
  }
}
