package com.example;

import java.util.ArrayList;
import java.util.List;

public class Registry<T> {
  private final List<String> names = new ArrayList<>();

  public void register(String name) {
    names.add(name);
  }

  public int size() {
    return names.size();
  }
}
