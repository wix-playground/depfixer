package com.wix.bazel.depfixer.overrides;

public class NoOverrides implements Overrides {
    @Override
    public String overrideClass(String classname) {
        return classname;
    }

    @Override
    public String overrideClassToTarget(String targetName) {
        return targetName;
    }
}
