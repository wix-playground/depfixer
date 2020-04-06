package com.wix.bazel.repo;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.io.Serializable;
import java.util.*;

public class RepoCache implements Serializable  {
    private Map<String, TargetsHolder> map = new HashMap<>();
    private transient TargetsStore targetsStore;
    private Map<String, Set<String>> jarClasses = new HashMap<>();

    public RepoCache(TargetsStore targetsStore) {
        this.targetsStore = targetsStore;
    }

    public void put(String srcjar, String cls, String target) {
        if (targetsStore.ignoreTarget(target))
            return;

        TargetsHolder holder = map.computeIfAbsent(cls, k -> new TargetsHolder());

        if (targetsStore.isTestOnly(target)) {
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

            if (holder == null)
                continue;

            holder.clear(srcjar);

            if (holder.isEmpty())
                map.remove(cls);
        }
    }

    public String get(String cls, BrokenTargetData targetData) {
        return get(cls, targetData, Collections.emptySet());
    }

    public String get(String cls, BrokenTargetData targetData, Set<String> targetHistory) {
        TargetsHolder holder = map.get(cls);

        if (holder == null) {
            return null;
        }

        return holder.getTarget(targetData, targetHistory);
    }

    public Set<String> getClasses() {
        return map.keySet();
    }

    public void setTargetStore(TargetsStore targetStore) {
        this.targetsStore = targetStore;
    }
}
