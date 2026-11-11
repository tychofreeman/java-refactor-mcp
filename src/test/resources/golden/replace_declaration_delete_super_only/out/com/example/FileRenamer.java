package com.example;

public class FileRenamer extends Renamer {
  boolean both(String a) {
    return carryOut(a) && new FileRenamer().carryOut(a);
  }
}
