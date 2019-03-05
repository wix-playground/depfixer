package com.wix.bazel.repo;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TargetsHolder {
    private String testTarget;
    private Set<String> prodTargets = new HashSet<>();

    String getTarget(BrokenTargetData forTarget) {
        String target = getTargetInternal(forTarget);
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

    private String getTargetInternal(BrokenTargetData forTarget) {
        if (forTarget.isTestOnly()) {
            Optional<String> maybeProdTarget = prodTargets.stream().findFirst();
            return maybeProdTarget.orElseGet(() -> testTarget);
        }

        if (prodTargets.isEmpty()) {
            return null;
        }

        Optional<String> maybeProdTarget = prodTargets.stream().filter(t -> !targetIsImplicitTest(t)).findFirst();
        return maybeProdTarget.orElse((prodTargets.stream().findFirst()).orElse(null));
    }

    private static boolean targetIsImplicitTest(String target) {
        return target.contains("e2e")
                || target.contains("testkit")
                || target.contains("mock")
                || target.contains("/it/");
    }

    public void setTestTarget(String target) {
        this.testTarget = target;
    }

    public void setProdTarget(String target) {
        this.prodTargets.add(target);
    }
}
