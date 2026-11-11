package com.example;

import java.util.List;

record Pair(List<String> a, List<String> b) {
  static List<String> use(Pair p) {
    return List.of(String.valueOf(p.a().size()), String.valueOf(p.b()));
  }
}
