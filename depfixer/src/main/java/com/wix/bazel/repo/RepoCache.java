package com.wix.bazel.repo;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RepoCache implements Serializable  {
    private Map<String, TargetsHolder> map = new HashMap<>();
    private Set<String> testTargets;
    private Map<String, Set<String>> jarClasses = new HashMap<>();

    public RepoCache(Set<String> testTargets) {
        this.testTargets = testTargets;
    }

    public void put(String srcjar, String cls, String target) {
        TargetsHolder holder = map.computeIfAbsent(cls, k -> new TargetsHolder());

        if (testTargets.contains(target)) {
            holder.setTestTarget(srcjar, target);
        } else {
            holder.setProdTarget(srcjar, target);
        }

        Set<String> classes = jarClasses.computeIfAbsent(srcjar, x -> new HashSet<>());
        classes.add(cls);
    }

    public void clear(String srcjar) {
        Set<String> classes = jarClasses.remove(srcjar);

        if (classes == null) return;

        for (String cls : classes) {
            TargetsHolder holder = map.get(cls);

            holder.clear(srcjar);

            if (holder.isEmpty())
                map.remove(cls);
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

    public void setTestTargets(Set<String> testTargets) {
        this.testTargets = testTargets;
    }
}
