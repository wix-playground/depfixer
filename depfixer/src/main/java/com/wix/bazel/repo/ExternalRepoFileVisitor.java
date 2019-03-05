package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExternalRepoFileVisitor extends SimpleFileVisitor<Path> {
    private final RepoCache cache;
    private final Path start;
    private final List<String> filteredJars;
    private final String workspaceName;
    private final RunMode runMode;

    public ExternalRepoFileVisitor(Path start,
                                   Set<String> testOnlyTargets,
                                   String workspaceName,
                                   RunMode runMode) {
        this.start = start;
        InputStream stream  = this.getClass().getClassLoader().getResourceAsStream("jars.txt");
        filteredJars = new BufferedReader(new InputStreamReader(stream))
                .lines().collect(Collectors.toList());

        this.cache = new RepoCache(testOnlyTargets);
        this.workspaceName = workspaceName;
        this.runMode = runMode;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (!excludedJar(file) && attrs.isRegularFile() && isCodeJar(file)) {
            String target = start.relativize(file.getParent()).toString();

            if (target.contains("com_google_collections_google_collections")) {
                return FileVisitResult.CONTINUE;
            }

            if (target.contains("/") && !target.contains("/jar")) return FileVisitResult.CONTINUE;

            target = target.replace("/jar", "//jar");

            JarFileProcessor.addClassesToCache(file, "@" + target, cache);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir.startsWith(start.resolve(workspaceName))) {
            return FileVisitResult.SKIP_SUBTREE;
        }

        return super.preVisitDirectory(dir, attrs);
    }

    private boolean excludedJar(Path file) {
        if (runMode == RunMode.ISOLATED) {
            return false;
        } else {
            String fileName = file.toString();
            return filteredJars.stream().anyMatch(fileName::contains);
        }
    }

    private boolean isCodeJar(Path file) {
        String fileName = file.toString();
        return fileName.endsWith(".jar") && !fileName.endsWith("-src.jar");
    }

    public RepoCache getClassToTarget() {
        return cache;
    }
}
