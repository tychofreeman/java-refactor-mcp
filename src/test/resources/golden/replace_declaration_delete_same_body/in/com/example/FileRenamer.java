package com.example;

public class FileRenamer extends Renamer {
  @Override
  boolean carryOut(String r) {
    print(r);
    return move(r);
  }

  @Override
  boolean move(String r) {
    return true;
  }
}
