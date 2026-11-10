package com.example;

import java.io.PrintStream;
import java.util.List;

/**
 * Renames files.
 */
public class Main {
    static int run(List<String> names, PrintStream out) {
        int status = 0;
        for (String r : names) {
            out.println(r + " -> " + r.length());
            if (r.isEmpty()) {
                status = 1;
            }
        }
        return status;
    }
}
