package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

public class InternalRepoFileVisitor extends SimpleFileVisitor<Path> {
    private final String workspaceName;
    private final RepoCache classToTarget;
    private final Path start, repoRoot, externalPath;
    private final RunMode runMode;

    public InternalRepoFileVisitor(Path start,
                                   Path repoRoot,
                                   Path externalPath,
                                   Set<String> testTarget,
                                   String workspaceName,
                                   RunMode runMode) {
        this.start = start;
        this.repoRoot = repoRoot;
        this.externalPath = externalPath;
        this.classToTarget = new RepoCache(testTarget);
        this.workspaceName = workspaceName;
        this.runMode = runMode;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (attrs.isRegularFile() && isCodeJar(file)) {
            String target = getTargetName(file);

            if (target == null) {
                return FileVisitResult.CONTINUE;
            }

            JarFileProcessor.addClassesToCache(file, target, classToTarget);
        }

        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path relTarget = start.relativize(dir);
        if (relTarget.startsWith("external/" + workspaceName) ||
                dir.toString().contains("external/bazel_tools") ||
                dir.toString().contains("external/io_bazel_rules") ||
                dir.toString().contains(".runfiles/") ||
                skipExternalWhenIsolatedMode(relTarget)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    private boolean skipExternalWhenIsolatedMode(Path relTarget) {
        return relTarget.toString().startsWith("external/") && runMode == RunMode.ISOLATED;
    }

    private String getTargetName(Path file) throws IOException {
        Path relTarget = start.relativize(file);

        boolean external = relTarget.toString().startsWith("external/");

        Path repoRootPath = external ? externalPath : repoRoot;

        Path targetPath = null;

        for(int i = (external ? 1 : 0); i < relTarget.getNameCount(); i++) {

            Path aggregatedPath = null;

            if (targetPath == null) {
                aggregatedPath = relTarget.getName(i);
            } else {
                aggregatedPath = targetPath.resolve(relTarget.getName(i));
            }

            Path repoPath = repoRootPath.resolve(aggregatedPath);

            if (Files.isDirectory(repoPath)) {
                targetPath = aggregatedPath;
            } else {
                break;
            }
        }

        String fileName = external ?
                start.resolve("external").resolve(targetPath).relativize(file).toString() :
                start.resolve(targetPath).relativize(file).toString();
        String fileTargetName = fileName.replace("_java", "").replace(".jar", "");

        if (targetDoesNotExist(repoRootPath.resolve(targetPath), fileTargetName)) {
            return null;
        }

        return  external ?
                String.format("@%s:%s", targetPath, fileTargetName).replaceFirst("/", "//") :
                String.format("//%s:%s", targetPath, fileTargetName);
    }

    private boolean targetDoesNotExist(Path packagePath, String targetName) throws IOException {
        return !targetExist(packagePath, targetName);
    }

    private boolean targetExist(Path packagePath, String targetName) throws IOException {
        if (!targetName.contains("/")) {
            return true;
        }

        Path buildFile = packagePath.resolve("BUILD.bazel");

        if (!Files.isRegularFile(buildFile))
            buildFile = packagePath.resolve("BUILD");

        if (!Files.isRegularFile(buildFile))
            return false;

        String buildFileContent = new String(Files.readAllBytes(buildFile));

        return buildFileContent.contains(String.format("\"%s\"", targetName));
    }

    private boolean isCodeJar(Path file) {
        String fileName = file.toString();
        return fileName.endsWith(".jar") &&
                !fileName.endsWith("-src.jar") &&
                !fileName.endsWith("-ijar.jar") &&
                !fileName.endsWith("_java-hjar.jar") &&
                !fileName.endsWith("_java-native-header.jar") &&
                !fileName.endsWith("_manifest_jar.jar") &&
                !fileName.endsWith("_deploy.jar")
                ;
    }

    public RepoCache getClassToTarget() {
        return classToTarget;
    }
}
