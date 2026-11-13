package com.example;

import java.io.IOException;

class FileStore implements Store {
  @Override
  public void save(String s) throws IOException {
    System.out.println(s);
  }
}
