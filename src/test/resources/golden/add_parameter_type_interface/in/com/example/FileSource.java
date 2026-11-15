package com.example;

class FileSource implements Source {
  @Override
  public String next() {
    return "f";
  }
}
