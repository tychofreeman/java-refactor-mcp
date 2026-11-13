package com.example;

import java.util.List;

public interface Listing<K, V extends List<K>> {
  V values();
}
