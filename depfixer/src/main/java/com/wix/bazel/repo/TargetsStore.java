package com.wix.bazel.repo;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class TargetsStore implements Serializable  {
    private final Set<String> testOnlyTargets = new HashSet<>();
    private final Set<String> targetsToIgnore = new HashSet<>();

    public final void updateTestOnlyTargets(Set<String> testOnlyTargets) {
        this.testOnlyTargets.addAll(testOnlyTargets);
    }

    public final void updateTargetsToIgnore(Set<String> targetsToIgnore) {
        this.targetsToIgnore.addAll(targetsToIgnore);
    }

    public final boolean isTestOnly(String target) {
        return testOnlyTargets.contains(target);
    }

    public final boolean ignoreTarget(String target) {
        return targetsToIgnore.contains(target);
    }
}
