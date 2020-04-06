package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

public interface TargetAnalyzer {
    AnalyzerResult analyze(BrokenTargetData targetData);

    default boolean isEnabled() {
        return true;
    }
}
