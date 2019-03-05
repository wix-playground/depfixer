package com.wix.bazel.repo;

import com.wix.bazel.runmode.RunMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class RepoCacheBuilder {
    private final Path repoPath;

    public RepoCacheBuilder(Path repoPath) {
        this.repoPath = repoPath;
    }

    public RepoCache buildExternal(Path bazelExternalPath,
                                   Set<String> testOnlyTargets,
                                   String workspaceName,
                                   RunMode runMode) throws IOException {
        ExternalRepoFileVisitor externalRepoFileVisitor = new ExternalRepoFileVisitor(
                bazelExternalPath,
                testOnlyTargets,
                workspaceName,
                runMode);
        Files.walkFileTree(bazelExternalPath, externalRepoFileVisitor);

        return externalRepoFileVisitor.getClassToTarget();
    }

    public RepoCache buildInternal(Path bazelOutPath,
                                   Path bazelExternalPath,
                                   Set<String> testOnlyTargets,
                                   String workspaceName,
                                   RunMode runMode) throws IOException {
        InternalRepoFileVisitor fileVisitor = new InternalRepoFileVisitor(
                bazelOutPath,
                repoPath,
                bazelExternalPath,
                testOnlyTargets,
                workspaceName,
                runMode);
        Files.walkFileTree(bazelOutPath, fileVisitor);

        return fileVisitor.getClassToTarget();
    }
}
