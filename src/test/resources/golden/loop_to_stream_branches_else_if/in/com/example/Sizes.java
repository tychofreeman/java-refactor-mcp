package com.example;

import java.util.ArrayList;
import java.util.List;

public class Sizes {
  void show(List<Integer> sizes) {
    List<String> labels = new ArrayList<>();
    for (Integer size : sizes) {
      if (size > 100) {
        labels.add("big");
      } else if (size > 10) {
        labels.add("medium");
      } else {
      }
    }
    System.out.println(labels.size());
  }
}
