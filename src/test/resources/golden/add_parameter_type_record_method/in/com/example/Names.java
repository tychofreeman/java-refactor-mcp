package com.example;

import java.util.List;

record Names(List<String> all) {
  String pick(List<String> xs) {
    return xs.isEmpty() ? null : xs.get(0);
  }
}
