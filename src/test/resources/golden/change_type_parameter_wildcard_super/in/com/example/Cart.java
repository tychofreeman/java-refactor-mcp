package com.example;

import java.util.ArrayList;
import java.util.List;

public class Cart {
  private final List<Item> lines = new ArrayList<>();

  void drainTo(List<Item> out) {
    out.addAll(lines);
  }
}
