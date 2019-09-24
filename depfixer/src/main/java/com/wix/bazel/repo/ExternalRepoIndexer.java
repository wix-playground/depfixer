package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExternalRepoIndexer extends AbstractBazelIndexer {
    private final Set<String> filteredJars;

    public ExternalRepoIndexer(Path repoRoot, Path persistencePath, String workspaceName, RunMode runMode,
                               Path directoryToScan, Set<String> testOnlyTargets) {
        super(repoRoot, persistencePath, workspaceName, runMode, directoryToScan, testOnlyTargets);

        InputStream stream = this.getClass().getClassLoader().getResourceAsStream("jars.txt");
        filteredJars = new BufferedReader(new InputStreamReader(stream))
                .lines().collect(Collectors.toSet());
    }

    @Override
    protected List<String> gitIgnoreContent() {
        return Arrays.asList(
                workspaceName,
                "com_google_collections_google_collections/",
                "com_wix_wix_embedded_mysql_download_and_extract_jar_with_dependencies/");
    }

    @Override
    protected boolean isCodejar(Path jar) {
        String targetName = partialTargetName(jar);
        return isValid(targetName) &&
                !excludedJar(targetName);
    }

    @Override
    protected String getTargetName(Path jar) {
        String targetName = partialTargetName(jar);
        return "@" + targetName.replace("/jar", "//jar");
    }

    private String partialTargetName(Path jar) {
        return directoryToIndex.relativize(jar.getParent()).toString();
    }

    private boolean isValid(String targetName) {
        return !targetName.contains("/") || targetName.contains("/jar");
    }

    private boolean excludedJar(String targetName) {
        if (runMode == RunMode.ISOLATED) {
            return false;
        } else {
            return filteredJars.contains(targetName);
        }
    }
}
