package com.example;

import java.util.List;

record Names(List<String> all) {
  <T> T pick(List<T> xs) {
    return xs.isEmpty() ? null : xs.get(0);
  }
}
