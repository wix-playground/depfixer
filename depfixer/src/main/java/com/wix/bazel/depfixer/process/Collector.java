package com.wix.bazel.depfixer.process;

import java.util.function.Consumer;

public class Collector {
    private final boolean quiet;
    private Consumer<String> consumer;
    private StringBuilder content;

    public Collector(Consumer<String> consumer, boolean quiet) {
        this.consumer = consumer;
        this.content = new StringBuilder();
        this.quiet = quiet;
    }

    public void collect(String line) {
        consumer.accept(line);

        if (quiet) return;

        if (content.length() > 0) {
            content.append("\n");
        }

        content.append(line);
    }

    public String getCollected() {
        return content.toString();
    }
}
