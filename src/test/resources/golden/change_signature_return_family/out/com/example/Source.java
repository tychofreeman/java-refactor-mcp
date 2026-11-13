package com.example;

import java.io.IOException;

interface Source {
  CharSequence read() throws IOException, IllegalStateException;
}
