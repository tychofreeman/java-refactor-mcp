package com.example;

import java.util.List;

interface Source {
  <T> T pick(List<T> xs);
}
