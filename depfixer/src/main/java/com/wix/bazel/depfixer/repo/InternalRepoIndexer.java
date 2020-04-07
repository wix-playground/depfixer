package com.wix.bazel.depfixer.repo;

import com.wix.bazel.depfixer.cache.TargetsStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class InternalRepoIndexer extends AbstractBazelIndexer {
    private final Path externalPath;

    public InternalRepoIndexer(Path repoRoot, Path persistencePath, String workspaceName,
                               Path directoryToScan, TargetsStore targetsStore,
                               Path externalPath) {
        super(repoRoot, persistencePath, workspaceName, directoryToScan, targetsStore);
        this.externalPath = externalPath;
    }

    @Override
    protected List<String> gitIgnoreContent() {
        return Arrays.asList(
                "",
                "external/" + workspaceName + "/",
                "external/bazel_tools/",
                "external/io_bazel_rules/",
                "*.runfiles/",
                "*-ijar.jar",
                "*-hjar.jar",
                "*-native-header.jar",
                "*_manifest_jar.jar",
                "*_deploy.jar",
                "*-deployable.jar",
                "*_test_runner.jar",
                "main_dependencies.jar",
                "test_dependencies.jar",
                "*_scalapb.jar"
        );
    }

    @Override
    protected boolean isCodejar(Path jar) {
        return true;
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

        if (targetPath == null) {
            System.out.println("InternalRepoIndexer : ignoring jar " + jar);
            return null;
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

        if (fileTargetName.startsWith("lib")) {
            try {
                Manifest m = new JarFile(jar.toFile()).getManifest();
                Attributes attrs = m.getMainAttributes();

                if (attrs != null && !attrs.isEmpty()) {
                    String label = attrs.getValue("Target-Label");

                    if (label != null && !label.isEmpty()) {
                        return label;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
}
