package com.example;

import java.util.HashMap;
import java.util.Map;

public class Stored<V> {

  protected final Map<String, V> entries = new HashMap<>();

  public V first() {
    return entries.values().iterator().next();
  }
}
