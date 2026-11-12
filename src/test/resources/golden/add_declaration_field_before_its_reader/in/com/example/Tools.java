package com.example;

import java.util.List;

class Tools {
  static final String RENAME = "rename";
  static final List<String> ALL = List.of(RENAME);

  static int count() {
    return ALL.size();
  }
}
