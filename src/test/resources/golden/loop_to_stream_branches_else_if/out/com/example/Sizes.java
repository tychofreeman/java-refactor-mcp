package com.example;

import java.util.List;
import java.util.stream.Stream;

public class Sizes {
  void show(List<Integer> sizes) {
    List<String> labels = sizes.stream()
        .flatMap(size -> size > 100 ? Stream.of("big") : size > 10 ? Stream.of("medium") : Stream.empty())
        .toList();
    System.out.println(labels.size());
  }
}
