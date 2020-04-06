package com.wix.bazel.depfixer.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProcessRunner {
    private ProcessRunner() {}

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static ExecuteResult execute(Path path, String... args)
            throws IOException, InterruptedException, ExecutionException {
        return execute(path, Collections.emptyMap(), args);
    }

    public static ExecuteResult execute(Path path, Map<String, String> env, String... args)
            throws IOException, InterruptedException, ExecutionException {
        ProcessBuilder builder = new ProcessBuilder();

        builder.command(args);
        builder.directory(path.toFile());
        builder.environment().putAll(env);

        Process process = builder.start();
        StreamGobbler stdout =
                new StreamGobbler(process.getInputStream(), System.out::println);
        StreamGobbler stderr =
                new StreamGobbler(process.getErrorStream(), System.err::println);

        Future<String> stdoutF = executorService.submit(stdout);
        Future<String> stderrF = executorService.submit(stderr);

        int exitCode = process.waitFor();

        ExecuteResult res = new ExecuteResult();

        res.command = String.join(" ", Arrays.asList(args));
        res.exitCode = exitCode;
        res.stderr = stderrF.get();
        res.stdout = stdoutF.get();

        return res;
    }

    public static ExecuteResult quiteExecute(Path path, Map<String, String> env, String... args) {
        ProcessBuilder builder = new ProcessBuilder();

        builder.command(args);
        builder.directory(path.toFile());
        builder.environment().putAll(env);

        Process process = null;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> outList = new LinkedList<>();
        List<String> errList = new LinkedList<>();

        StreamGobbler stdout =
                new StreamGobbler(process.getInputStream(), outList::add);
        StreamGobbler stderr =
                new StreamGobbler(process.getErrorStream(), outList::add);

        executorService.submit(stdout);
        executorService.submit(stderr);

        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ExecuteResult res = new ExecuteResult();

        res.command = String.join(" ", Arrays.asList(args));
        res.exitCode = exitCode;
        res.stdoutLines = outList;
        res.stderrLines = errList;

        return res;
    }

    public static void shutdownNow() {
        executorService.shutdownNow();
    }

    public static void shutdown() {
        executorService.shutdown();
    }
}
