package com.example;

import java.util.ArrayList;
import java.util.List;

class Names {
  <T> T head(List<T> xs) {
    return xs.get(0);
  }

  Object any(Names other) {
    Object mine = this.<String>head(new ArrayList<>());
    return mine == null ? other.<String>head(new ArrayList<>()) : mine;
  }
}
