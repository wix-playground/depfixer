package com.wix.bazel.depfixer.analyze;

public abstract class AbstractTargetAnalyzer implements TargetAnalyzer {
    protected final AnalyzerContext ctx;

    protected AbstractTargetAnalyzer(AnalyzerContext ctx) {
        this.ctx = ctx;
    }
}
