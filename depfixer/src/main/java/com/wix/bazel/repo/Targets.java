package com.wix.bazel.repo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Targets {
    private static final Set<String> Excluded = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("com_wix_wix_embedded_mysql_download_and_extract_jar_with_dependencies")));

    public static boolean notExcluded(String target) {
        return !Excluded.contains(target);
    }
}
