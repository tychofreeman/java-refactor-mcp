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
            status = carryOut(out, r, status);
        }
        return status;
    }

    private static int carryOut(PrintStream out, String r, int status) {
        out.println(r + " -> " + r.length());
        if (r.isEmpty()) {
            status = 1;
        }
        return status;
    }
}
