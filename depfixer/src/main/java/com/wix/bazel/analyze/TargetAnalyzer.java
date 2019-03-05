package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.Set;

public interface TargetAnalyzer {
    AnalyzerResult analyze(BrokenTargetData targetData);

    default boolean isEnabled() {
        return true;
    }
}
