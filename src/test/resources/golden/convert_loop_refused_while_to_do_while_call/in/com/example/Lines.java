package com.example;

import java.util.Iterator;

public class Lines {
  void print(Iterator<String> it) {
    while (it.hasNext()) {
      System.out.println(it.next());
    }
  }
}
