package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

abstract public class AbstractBazelIndexer extends SimpleFileVisitor<Path> {
    final Path repoRoot;
    final String workspaceName;
    final RunMode runMode;
    final Path directoryToIndex;

    private RepoCache classToTarget;
    private Map<String, CodeJar> cachedJars;
    private Set<String> indexedJars;

    private final Path persistencePath;
    private final AtomicInteger hitsCounter = new AtomicInteger(0);
    private final AtomicInteger jarsCounter = new AtomicInteger(0);

    AbstractBazelIndexer(Path repoRoot, Path persistencePath, String workspaceName,
                         RunMode runMode, Path directoryToIndex,
                         Set<String> testOnlyTargets) {
        this.repoRoot = repoRoot;
        this.workspaceName = workspaceName;
        this.runMode = runMode;
        this.directoryToIndex = directoryToIndex;
        this.persistencePath = persistencePath;

        initFromDisk(testOnlyTargets);
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!attrs.isRegularFile() || !jarNeedsToBeIndexed(file)) {
            return FileVisitResult.CONTINUE;
        }

        String targetName = getTargetName(file);

        CodeJar codeJar = new CodeJar(file, attrs);
        CodeJar prevCodeJar = cachedJars.get(codeJar.path);

        if (prevCodeJar != null) {
            if (Objects.equals(codeJar, prevCodeJar)) {
                hitsCounter.incrementAndGet();
                indexedJars.add(codeJar.path);
                jarsCounter.incrementAndGet();

                return FileVisitResult.CONTINUE;
            } else {
                classToTarget.clear(codeJar.path);
                cachedJars.remove(codeJar.path);
            }
        }

        if (targetName == null) {
            return FileVisitResult.CONTINUE;
        }

        jarsCounter.incrementAndGet();

        JarFileProcessor.addClassesToCache(file, targetName, classToTarget);
        cachedJars.put(codeJar.path, codeJar);
        indexedJars.add(codeJar.path);

        return FileVisitResult.CONTINUE;
    }

    @SuppressWarnings("RedundantThrows")
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (directoryNeedsToBeIndexed(dir)) {
            return FileVisitResult.CONTINUE;
        }

        return FileVisitResult.SKIP_SUBTREE;
    }

    public final RepoCache index() {
        time("total indexing time:", () -> {
            beforeIndexing();

            try {
                Files.walkFileTree(directoryToIndex, this);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            afterIndexing();
        });

        return classToTarget;
    }

    private void beforeIndexing() {
        indexedJars = new HashSet<>();
        hitsCounter.set(0);
        jarsCounter.set(0);
    }

    private void afterIndexing() {
        int totalRemoved = 0;
        for(String jar : new HashSet<>(cachedJars.keySet())) {
            if (indexedJars.contains(jar)) continue;

            classToTarget.clear(jar);
            cachedJars.remove(jar);

            totalRemoved++;
        }

        if (jarsCounter.get() != hitsCounter.get())
            time("saving index to file:", this::saveToDisk);

        System.out.println(this.getClass().getName() + " total jars: " + jarsCounter.get());
        System.out.println(this.getClass().getName() + " total hits: " + hitsCounter.get());
        System.out.println(this.getClass().getName() + " total removed: " + totalRemoved);
    }

    private void initFromDisk(Set<String> testOnlyTargets) {
        time("loading index from disk", this::loadFromDisk);

        if (this.classToTarget == null)
            this.classToTarget = new RepoCache(testOnlyTargets);
        else
            this.classToTarget.setTestTargets(testOnlyTargets);

        if (this.cachedJars == null)
            this.cachedJars = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        Path diskCopy = getDiskCopyFilename();

        if (!Files.isRegularFile(diskCopy)) return;

        try (FileInputStream fis = new FileInputStream(diskCopy.toFile())) {
            try (ObjectInputStream ois = new ObjectInputStream(new InflaterInputStream(fis))) {
                classToTarget = (RepoCache) ois.readObject();
                cachedJars = (Map<String, CodeJar>) ois.readObject();
            }
        } catch (Exception e) {
            System.err.println("Failed to load index from disk");
            e.printStackTrace();

            classToTarget = null;
            cachedJars = null;
        }
    }

    private void time(String message, Runnable runnable) {
        Instant start = Instant.now();

        runnable.run();

        Instant end = Instant.now();

        System.out.println(this.getClass().getName() + " " + message + " " + Duration.between(start, end));
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
                oos.writeObject(cachedJars);
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

    protected abstract boolean isCodejar(Path jar);

    protected abstract boolean directoryNeedsToBeIndexed(Path dir);

    protected abstract String getTargetName(Path jar);

    private static class CodeJar implements Serializable {
        final String path;
        final long lastModifiedTime;

        CodeJar(Path jar, BasicFileAttributes attrs) {
            path = jar.toAbsolutePath().toString();
            lastModifiedTime = attrs.lastModifiedTime().toMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CodeJar codeJar = (CodeJar) o;
            return Objects.equals(path, codeJar.path) &&
                    Objects.equals(lastModifiedTime, codeJar.lastModifiedTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, lastModifiedTime);
        }
    }

}
