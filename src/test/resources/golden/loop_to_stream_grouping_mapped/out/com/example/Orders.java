package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Orders {
  Map<String, List<Integer>> ids(List<Order> orders) {
    Map<String, List<Integer>> ids = orders.stream()
        .collect(Collectors.groupingBy(
            order -> order.customer(),
            HashMap::new,
            Collectors.mapping(order -> order.id(), Collectors.toCollection(ArrayList::new))));
    return ids;
  }

  record Order(String customer, int id) {}
}
