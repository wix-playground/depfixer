package com.wix.bazel.repo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ExternalRepoIndexer extends AbstractBazelIndexer {

    public ExternalRepoIndexer(Path repoRoot, Path persistencePath, String workspaceName,
                               Path directoryToScan, Set<String> testOnlyTargets) {
        super(repoRoot, persistencePath, workspaceName, directoryToScan, testOnlyTargets);
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
        return isValid(targetName);
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

}
