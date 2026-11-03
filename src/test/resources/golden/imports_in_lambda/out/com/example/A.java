package com.example;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class A {
  int pushed;

  Map<String, Deque<String>> queues = new HashMap<>();

  void push(String key, String value) {
    queues.computeIfAbsent(key, k -> new ArrayDeque<>()).push(value);
  }
}
