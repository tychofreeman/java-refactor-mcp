package com.example;

import java.util.List;

public interface Picker {
  <T extends Comparable<T>> T pick(List<T> options);
}
