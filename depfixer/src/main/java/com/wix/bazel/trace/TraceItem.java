package com.wix.bazel.trace;

import com.wix.bazel.brokentarget.BrokenTargetData;

public class TraceItem {
    int runNumber;
    String analyzerClassName;
    BrokenTargetData targetData;
    String className;
    String resolvedTarget;
    boolean labeldex;

    public TraceItem(int runNumber, String analyzerClassName, BrokenTargetData targetData,
                     String className, String resolvedTarget, boolean labeldex) {
        this.runNumber = runNumber;
        this.analyzerClassName = analyzerClassName;
        this.targetData = targetData;
        this.className = className;
        this.resolvedTarget = resolvedTarget;
        this.labeldex = labeldex;
    }
}
