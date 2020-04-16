package com.wix.bazel.depfixer.brokentarget;

import java.nio.file.Path;
import java.util.List;

public interface BrokenTargetExtractorFactory {
    BrokenTargetExtractor create(Path repoPath, Path externalRepoPath, Path runPath, String stderr);

    List<String> initArgs(Path path, String targetsToBuild);
}
