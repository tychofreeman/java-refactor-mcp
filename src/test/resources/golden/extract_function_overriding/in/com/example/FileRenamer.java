package com.example;

public class FileRenamer extends Renamer {
  @Override
  boolean carryOut(String r) {
    print(r);
    System.out.println("moving " + r);
    return false;
  }
}
