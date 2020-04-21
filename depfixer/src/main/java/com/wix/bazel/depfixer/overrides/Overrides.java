package com.wix.bazel.depfixer.overrides;

public interface Overrides {
    String overrideClass(String classname);

    String overrideClassToTarget(String targetName);
}
