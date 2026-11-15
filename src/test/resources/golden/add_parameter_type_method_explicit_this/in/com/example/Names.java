package com.example;

import java.util.ArrayList;
import java.util.List;

class Names {
  String head(List<String> xs) {
    return xs.get(0);
  }

  Object any(Names other) {
    Object mine = head(new ArrayList<>());
    return mine == null ? other.head(new ArrayList<>()) : mine;
  }
}
