package com.example;

public class FileRenamer extends Renamer {
  @Override
  boolean carryOut(String r) {
    return super.carryOut(r);
  }

  boolean both(String a) {
    return carryOut(a) && new FileRenamer().carryOut(a);
  }
}
