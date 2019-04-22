package com.wix.bazel.repo;

import com.wix.bazel.process.ExecuteResult;
import com.wix.bazel.process.ProcessRunner;
import com.wix.bazel.runmode.RunMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

abstract public class AbstractBazelIndexer {
    private static final List<String> BASE_IGNORE = Arrays.asList(
            "*",
            "!*/",
            "*remotejdk*/",
            "local_jdk/",
            "io_bazel_rules_scala*/",
            "resources/",
            "!*.jar",
            "*-src.jar",
            "*-sources.jar"
    );

    final Path repoRoot;
    final String workspaceName;
    final RunMode runMode;
    final Path directoryToIndex;

    private RepoCache classToTarget;

    private final Path persistencePath;
    private final AtomicInteger newlyIndexedJars = new AtomicInteger(0);
    private final Git git;

    AbstractBazelIndexer(Path repoRoot, Path persistencePath, String workspaceName,
                         RunMode runMode, Path directoryToIndex,
                         Set<String> testOnlyTargets) {
        this.repoRoot = repoRoot;
        this.workspaceName = workspaceName;
        this.runMode = runMode;
        this.directoryToIndex = directoryToIndex;
        this.persistencePath = persistencePath;

        initFromDisk(testOnlyTargets);
        this.git = time("git init: ", this::initGit).result;
    }

    private Git initGit() {
        try {
            List<String> content = new LinkedList<>(BASE_IGNORE);
            content.addAll(gitIgnoreContent());

            Path gitIgnorePath = directoryToIndex.resolve(".gitignore");
            Files.write(gitIgnorePath, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            return Git.init().setDirectory(directoryToIndex.toFile()).call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void indexFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!attrs.isRegularFile() || !jarNeedsToBeIndexed(file)) {
            return;
        }

        String targetName = getTargetName(file);

        if (targetName == null) {
            return;
        }

        newlyIndexedJars.incrementAndGet();

        JarFileProcessor.addClassesToCache(file, targetName, classToTarget);
    }

    public final RepoCache index() {
        TimeResult<Duration> res = time("total indexing time:", this::doIndex);

        if (res.result.getSeconds() > 3) {
            //TODO - execute async
            time("git gc: ", () -> ProcessRunner.quiteExecute(directoryToIndex, Collections.emptyMap(), "git", "gc"));
            time("git repack: ", () -> ProcessRunner.quiteExecute(directoryToIndex, Collections.emptyMap(), "git", "repack"));
        }

        return classToTarget;
    }

    private void beforeIndexing() {
        newlyIndexedJars.set(0);
    }

    private void afterIndexing() {
        if (newlyIndexedJars.get() > 0)
            time("saving index to file:", this::saveToDisk);

        System.out.println(this.getClass().getName() + " total jars: " + newlyIndexedJars.get());
    }

    private void initFromDisk(Set<String> testOnlyTargets) {
        time("loading index from disk", this::loadFromDisk);

        if (this.classToTarget == null)
            this.classToTarget = new RepoCache(testOnlyTargets);
        else
            this.classToTarget.setTestTargets(testOnlyTargets);
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        Path diskCopy = getDiskCopyFilename();

        if (!Files.isRegularFile(diskCopy)) {
            removeGit();
            return;
        }

        try (FileInputStream fis = new FileInputStream(diskCopy.toFile())) {
            try (ObjectInputStream ois = new ObjectInputStream(new InflaterInputStream(fis))) {
                classToTarget = (RepoCache) ois.readObject();
            }
        } catch (Exception e) {
            System.err.println("Failed to load index from disk");
            e.printStackTrace();

            classToTarget = null;
            removeGit();
        }
    }

    private void removeGit() {
        ProcessRunner.quiteExecute(directoryToIndex, Collections.emptyMap(), "rm", "-fr", ".git");
    }

    private Duration time(String message, Runnable runnable) {
        Instant start = Instant.now();

        runnable.run();

        Instant end = Instant.now();

        Duration duration = Duration.between(start, end);
        System.out.println(this.getClass().getName() + " " + message + " " + duration);

        return duration;
    }

    private <T> TimeResult<T> time(String message, Callable<T> callable) {
        Instant start = Instant.now();

        T res;
        try {
            res = callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Instant end = Instant.now();

        Duration duration = Duration.between(start, end);
        System.out.println(this.getClass().getName() + " " + message + " " + duration);

        return new TimeResult<>(duration, res);
    }

    private void saveToDisk() {
        Path diskCopy = getDiskCopyFilename();

        if (!Files.exists(diskCopy)) {
            try {
                Files.createDirectories(diskCopy.getParent());
                Files.createFile(diskCopy);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileOutputStream fos = new FileOutputStream(diskCopy.toFile())) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new DeflaterOutputStream(fos))) {
                oos.writeObject(classToTarget);
            }
        } catch (Exception e) {
            System.err.println("Failed to save index to disk");
            e.printStackTrace();
        }
    }

    private Path getDiskCopyFilename() {
        return persistencePath.resolve(workspaceName).resolve(this.getClass().getSimpleName() + ".ser");
    }

    private boolean jarNeedsToBeIndexed(Path jar) {
        return jar.toString().endsWith(".jar") && isCodejar(jar);
    }

    protected abstract List<String> gitIgnoreContent();

    protected abstract boolean isCodejar(Path jar);

    protected abstract String getTargetName(Path jar);

    private Duration doIndex() {
        beforeIndexing();

        TimeResult<ExecuteResult> executeResult;
        try {
            executeResult = time("git add: ", () -> ProcessRunner.quiteExecute(directoryToIndex, Collections.emptyMap(), "git", "add", "-v", "."));

            if (executeResult.result.stdoutLines.isEmpty()) {
                return executeResult.duration;
            }

            TimeResult<Status> statusResult =
                    time("git status: ", () -> git.status().call());

            Status status = statusResult.result;

            if (!status.isClean()) {
                status.getAdded().stream()
                        .map(directoryToIndex::resolve)
                        .filter(this::jarNeedsToBeIndexed)
                        .forEach(p -> {
                            try {
                                indexFile(p, Files.readAttributes(p, BasicFileAttributes.class));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                status.getRemoved().stream()
                        .map(directoryToIndex::resolve)
                        .filter(this::jarNeedsToBeIndexed)
                        .forEach(p -> classToTarget.clear(p.toAbsolutePath().toString()));

                status.getChanged().stream()
                        .map(directoryToIndex::resolve)
                        .filter(this::jarNeedsToBeIndexed)
                        .forEach(p -> {
                            try {
                                classToTarget.clear(p.toAbsolutePath().toString());
                                indexFile(p, Files.readAttributes(p, BasicFileAttributes.class));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });


                git.commit().setSign(Boolean.FALSE).setMessage("commit by depfixer").call();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        afterIndexing();

        return executeResult.duration;
    }

    private static final class TimeResult<T> {
        final Duration duration;
        final T result;

        TimeResult(Duration duration, T result) {
            this.duration = duration;
            this.result = result;
        }
    }
}
