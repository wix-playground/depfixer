package com.wix.bazel.depfixer.cache;

public interface ExternalCacheFactory {
    ExternalCache create(String workspace, TargetsStore targetsStore);
}
