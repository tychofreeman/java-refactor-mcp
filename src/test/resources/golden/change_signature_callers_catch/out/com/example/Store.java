package com.example;

import java.io.IOException;

interface Store {
  void save(String s) throws IOException;
}
