package com.example;

import java.io.IOException;

interface Source {
  String read() throws IOException, IllegalStateException;
}
