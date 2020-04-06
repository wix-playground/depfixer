package com.wix.bazel.repo;

import com.wix.bazel.configuration.Configuration;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TargetsStore extends TargetsStoreExtension implements Serializable {
    private final Set<String> testOnlyTargets = new HashSet<>();
    private final Set<String> targetsToIgnore = new HashSet<>();
    private List<TargetsStoreExtension> extensionList = new LinkedList<>();

    public TargetsStore(Path repoPath, Configuration configuration) {
        super(repoPath, configuration);
    }

    public void installExtension(TargetsStoreExtension extension) {
        extensionList.add(extension);
    }

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

    @Override
    public String getRealTargetName(String target) {
        return extensionList.stream()
                .map(e -> e.getRealTargetName(target))
                .filter(n -> n != null && !n.equals(target))
                .findFirst()
                .orElse(target);
    }

    @Override
    public String getAttributeForTarget(boolean testOnlyTarget) {
        return extensionList.stream()
                .map(e -> e.getAttributeForTarget(testOnlyTarget))
                .findFirst()
                .orElse("deps");
    }

    @Override
    public void update() {
        extensionList.forEach(TargetsStoreExtension::update);
    }
}
