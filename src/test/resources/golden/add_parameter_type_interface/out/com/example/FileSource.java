package com.example;

class FileSource implements Source<String> {
  @Override
  public String next() {
    return "f";
  }
}
