package com.example;

public class Main {
  public static void main(String[] args) {
    Renamer renamer = new Renamer(System.out, System.err);
    Renamer.carryOut(renamer, "a");
    Renamer.carryOut((new Renamer(System.out, System.err)), "b");
  }
}
