package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.repo.RepoCache;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class AnalyzerContext {
    private RepoCache externalCache;
    private RepoCache internalCache;
    private int runNumber = 0;
    private Set<String> targetHistory;
    private Map<String, String> fileToContentMap;

    private Path repoPath, bazelExternalPath, bazelOutPath;

    public void setExternalCache(RepoCache externalCache) {
        this.externalCache = externalCache;
    }

    public void setInternalCache(RepoCache internalCache) {
        this.internalCache = internalCache;
    }

    public void setTargetDepsHistory(Set<String> targetHistory) {
        this.targetHistory = targetHistory;
    }

    public void setFileToContentMap(Map<String, String> fileToContentMap) {
        this.fileToContentMap = fileToContentMap;
    }

    public RepoCache getExternalCache() {
        return externalCache;
    }

    public RepoCache getInternalCache() {
        return internalCache;
    }

    public Set<String> getTargetDepsHistory() {
        return targetHistory;
    }

    public Map<String, String> getFileToContentMap() {
        return fileToContentMap;
    }

    public int getRunNumber() {
        return runNumber;
    }

    public void setRunNumber(int runNumber) {
        this.runNumber = runNumber;
    }

    public void incrementRunNumber() {
        this.runNumber += 1;
    }

    public Path getBazelExternalPath() {
        return bazelExternalPath;
    }

    public void setBazelExternalPath(Path bazelExternalPath) {
        this.bazelExternalPath = bazelExternalPath;
    }

    public Path getBazelOutPath() {
        return bazelOutPath;
    }

    public void setBazelOutPath(Path bazelOutPath) {
        this.bazelOutPath = bazelOutPath;
    }

    public Path getRepoPath() {
        return repoPath;
    }

    public void setRepoPath(Path repoPath) {
        this.repoPath = repoPath;
    }
}
