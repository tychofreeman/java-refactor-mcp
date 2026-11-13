package com.example;

class FileStore implements Store {
  @Override
  public void save(String s) {
    System.out.println(s);
  }
}
