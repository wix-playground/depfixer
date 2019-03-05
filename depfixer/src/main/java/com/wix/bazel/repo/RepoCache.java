package com.wix.bazel.repo;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RepoCache {
    private Map<String, TargetsHolder> map = new HashMap<>();
    private Set<String> testTargets;

    public RepoCache(Set<String> testTargets) {
        this.testTargets = testTargets;
    }

    public void put(String cls, String target) {
        TargetsHolder holder = map.computeIfAbsent(cls, k -> new TargetsHolder());

        if (testTargets.contains(target)) {
            holder.setTestTarget(target);
        } else {
            holder.setProdTarget(target);
        }

    }

    public String get(String cls, BrokenTargetData targetData) {
        TargetsHolder holder = map.get(cls);

        if (holder == null) {
            return null;
        }

        return holder.getTarget(targetData);
    }

    public Set<String> getClasses() {
        return map.keySet();
    }
}
