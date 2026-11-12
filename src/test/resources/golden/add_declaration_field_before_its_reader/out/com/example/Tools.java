package com.example;

import java.util.List;

class Tools {
  static final String RENAME = "rename";
  static final String MOVE = "move";
  static final List<String> ALL = List.of(RENAME, MOVE);

  static int count() {
    return ALL.size();
  }
}
