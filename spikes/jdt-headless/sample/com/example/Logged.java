package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Logged {
    private static final Logger LOG = LoggerFactory.getLogger(Logged.class);
    public void run() {
        LOG.info("running {}", "x");
    }
}
