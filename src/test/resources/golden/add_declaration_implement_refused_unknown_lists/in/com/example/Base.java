package com.example;

import java.util.List;

class Base {
  String describe(int n, String s) {
    return s + n;
  }

  String describe(List<String> names) {
    return String.join(", ", names);
  }
}
