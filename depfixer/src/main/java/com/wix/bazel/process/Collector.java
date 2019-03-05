package com.wix.bazel.process;

import java.util.function.Consumer;

public class Collector {
    private Consumer<String> consumer;
    private StringBuilder content;

    public Collector(Consumer<String> consumer) {
        this.consumer = consumer;
        this.content = new StringBuilder();
    }

    public void collect(String line) {
        if (content.length() > 0) {
            content.append("\n");
        }

        content.append(line);
        consumer.accept(line);
    }

    public String getCollected() {
        return content.toString();
    }
}
