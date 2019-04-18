package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class InternalRepoIndexer extends AbstractBazelIndexer {
    private final Path externalPath;

    public InternalRepoIndexer(Path repoRoot, Path persistencePath, String workspaceName,
                               RunMode runMode, Path directoryToScan, Set<String> testOnlyTargets,
                               Path externalPath) {
        super(repoRoot, persistencePath, workspaceName, runMode, directoryToScan, testOnlyTargets);
        this.externalPath = externalPath;
    }

    @Override
    protected boolean isCodejar(Path jar) {
        String fileName = jar.toString();
        return !fileName.endsWith("-src.jar") &&
                !fileName.endsWith("-ijar.jar") &&
                !fileName.endsWith("_java-hjar.jar") &&
                !fileName.endsWith("_java-native-header.jar") &&
                !fileName.endsWith("_manifest_jar.jar") &&
                !fileName.endsWith("_deploy.jar")
                ;
    }

    @Override
    protected boolean directoryNeedsToBeIndexed(Path dir) {
        Path relTarget = directoryToIndex.relativize(dir);
        return !relTarget.startsWith("external/" + workspaceName) &&
                !dir.toString().contains("external/bazel_tools") &&
                !dir.toString().contains("external/io_bazel_rules") &&
                !dir.toString().contains(".runfiles/") &&
                !skipExternalWhenIsolatedMode(relTarget);
    }

    @Override
    protected String getTargetName(Path jar) {
        Path relTarget = directoryToIndex.relativize(jar);

        boolean external = relTarget.toString().startsWith("external/");

        Path repoRootPath = external ? externalPath : repoRoot;

        Path targetPath = null;

        for(int i = (external ? 1 : 0); i < relTarget.getNameCount(); i++) {

            Path aggregatedPath;

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
                directoryToIndex.resolve("external").resolve(targetPath).relativize(jar).toString() :
                directoryToIndex.resolve(targetPath).relativize(jar).toString();
        String fileTargetName = fileName.replace("_java", "").replace(".jar", "");

        try {
            if (targetDoesNotExist(repoRootPath.resolve(targetPath), fileTargetName)) {
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return  external ?
                String.format("@%s:%s", targetPath, fileTargetName).replaceFirst("/", "//") :
                String.format("//%s:%s", targetPath, fileTargetName);
    }

    private boolean skipExternalWhenIsolatedMode(Path relTarget) {
        return runMode == RunMode.ISOLATED && relTarget.toString().startsWith("external/");
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
}
