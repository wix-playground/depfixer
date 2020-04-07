package com.wix.bazel.depfixer.cache;

import java.util.List;

public interface ExternalCache {
    RepoCache get(List<String> classes);
}
