package com.wix.bazel.process;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class StreamGobbler implements Callable<String> {
    private InputStream inputStream;
    private Collector collector;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
        this(inputStream, consumer, false);
    }

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer, boolean quiet) {
        this.inputStream = inputStream;
        this.collector = new Collector(consumer, quiet);
    }

    public String getStream() {
        return collector.getCollected();
    }

    @Override
    public String call() throws Exception {
        new BufferedReader(new InputStreamReader(inputStream)).lines()
                .forEach(collector::collect);

        return getStream();
    }
}
