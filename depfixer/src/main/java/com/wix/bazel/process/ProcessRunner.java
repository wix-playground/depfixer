package com.wix.bazel.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProcessRunner {
    private ProcessRunner() {}

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static ExecuteResult execute(Path path, String... args) throws IOException, InterruptedException {
        return execute(path, Collections.emptyMap(), args);
    }

    public static ExecuteResult execute(Path path, Map<String, String> env, String... args) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();

        builder.command(args);
        builder.directory(path.toFile());
        builder.environment().putAll(env);

        Process process = builder.start();
        StreamGobbler stdout =
                new StreamGobbler(process.getInputStream(), System.out::println);
        StreamGobbler stderr =
                new StreamGobbler(process.getErrorStream(), System.err::println);

        executorService.submit(stdout);
        executorService.submit(stderr);

        int exitCode = process.waitFor();
        ExecuteResult res = new ExecuteResult();

        res.command = String.join(" ", Arrays.asList(args));
        res.exitCode = exitCode;
        res.stderr = stderr.getStream();
        res.stdout = stdout.getStream();

        return res;
    }

    public static void shutdownNow() {
        executorService.shutdownNow();
    }

    public static void shutdown() {
        executorService.shutdown();
    }
}
