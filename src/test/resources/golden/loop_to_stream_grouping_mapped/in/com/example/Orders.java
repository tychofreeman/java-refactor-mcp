package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Orders {
  Map<String, List<Integer>> ids(List<Order> orders) {
    Map<String, List<Integer>> ids = new HashMap<>();
    for (Order order : orders) {
      ids.computeIfAbsent(order.customer(), k -> new ArrayList<>()).add(order.id());
    }
    return ids;
  }

  record Order(String customer, int id) {}
}
