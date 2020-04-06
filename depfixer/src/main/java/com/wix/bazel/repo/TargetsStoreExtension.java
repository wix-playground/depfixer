package com.wix.bazel.repo;

import com.wix.bazel.configuration.Configuration;

import java.nio.file.Path;

public abstract class TargetsStoreExtension {
    protected final Path repoPath;
    protected final Configuration configuration;

    protected TargetsStoreExtension(Path repoPath, Configuration configuration) {
        this.repoPath = repoPath;
        this.configuration = configuration;
    }

    public abstract String getRealTargetName(String target);
    public abstract String getAttributeForTarget(String target, boolean testOnlyTarget);
    public abstract void update();
}
