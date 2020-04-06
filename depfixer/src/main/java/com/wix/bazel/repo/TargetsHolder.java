package com.wix.bazel.repo;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TargetsHolder implements Serializable {
    private TargetWrapper testTarget;
    private Set<TargetWrapper> prodTargets = new HashSet<>();

    void clear(String jar) {
        if (testTarget != null && jar.equals(testTarget.srcjar)) {
            testTarget = null;
        }

        prodTargets = prodTargets.stream()
                .filter(x -> !jar.equals(x.srcjar)).collect(Collectors.toSet());
    }

    boolean isEmpty() {
        return testTarget == null && prodTargets.isEmpty();
    }

    String getTarget(BrokenTargetData forTarget, Set<String> targetHistory) {
        String target = getTargetInternal(forTarget, targetHistory);
        return normalizeTarget(forTarget, target);
    }

    public static String normalizeTarget(BrokenTargetData forTarget, String target) {
        if (target == null) {
            return null;
        }

        if (forTarget.getName().equals(target) || target.endsWith(forTarget.getName())) {
            return null;
        }

        if (forTarget.isExternal()) {
            String[] missingTargetParts = target.split("//");

            if (missingTargetParts.length > 1) {
                String[] forTargetParts = forTarget.getName().split("//");

                //if the missing target and the target it should be added to are from the same external workspace
                //remove the repo name from the missing target
                if (forTargetParts[0].equals(missingTargetParts[0])) {
                    target = "//" + missingTargetParts[1];
                } else if (missingTargetParts[0].isEmpty()) {
                    target = "@" + target; //missing target was found from current workspace
                }
            }
        }

        return target;
    }

    private String getTargetInternal(BrokenTargetData forTarget, Set<String> targetHistory) {
        Optional<TargetWrapper> maybeCandidateProdTarget = prodTargets.stream().filter(tw -> !targetHistory.contains(tw.target())).findFirst();

        if (forTarget.isTestOnly()) {
            return or(maybeCandidateProdTarget, Optional.ofNullable(testTarget)).map(TargetWrapper::target).orElse(null);
        }

        if (prodTargets.isEmpty()) {
            return null;
        }

        Optional<TargetWrapper> maybeProdTarget = prodTargets.stream()
                .filter(t -> !targetIsImplicitTest(t.target))
                .filter(tw -> !targetHistory.contains(tw.target()))
                .findFirst();
        return or(maybeProdTarget, or(maybeCandidateProdTarget, prodTargets.stream().findFirst()))
                .map(TargetWrapper::target).orElse(null);
    }

    private static boolean targetIsImplicitTest(String target) {
        return target.contains("e2e")
                || target.contains("testkit")
                || target.contains("mock")
                || target.contains("/it/");
    }

    public void setTestTarget(String srcjar, String target) {
        this.testTarget = new TargetWrapper(srcjar, target);
    }

    public void setProdTarget(String srcjar, String target) {
        this.prodTargets.add(new TargetWrapper(srcjar, target));
    }

    private static <T> Optional<T> or(Optional<T> first, Optional<T> second) {
        return first.isPresent() ? first : second;
    }

    private static final class TargetWrapper implements Serializable {
        String srcjar, target;

        public TargetWrapper(String srcjar, String target) {
            this.srcjar = srcjar;
            this.target = target;
        }

        public String target() {
            return target;
        }
    }
}
