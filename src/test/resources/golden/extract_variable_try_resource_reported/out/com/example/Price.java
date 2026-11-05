package com.example;

import java.io.IOException;
import java.io.StringReader;

public class Price {
  int f(Item p) {
    String n = p.name;
    try (StringReader r = new StringReader(n)) {
      return r.read();
    } catch (RuntimeException | IOException e) {
      return -1;
    }
  }
}

class Item {
  String name;
}
