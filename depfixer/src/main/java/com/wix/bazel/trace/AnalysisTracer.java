package com.wix.bazel.trace;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.ArrayList;
import java.util.List;

public class AnalysisTracer {
    private List<TraceItem> data = new ArrayList<>();

    public void trace(int runNumber, String analyzerClassName, BrokenTargetData targetData, String className, String resolvedTarget) {
        trace(runNumber, analyzerClassName, targetData, className, resolvedTarget, false);
    }

    public void trace(int runNumber, String analyzerClassName, BrokenTargetData targetData, String className, String resolvedTarget, boolean labeldex) {
        TraceItem traceItem = new TraceItem(runNumber, analyzerClassName, targetData, className, resolvedTarget, labeldex);
        data.add(traceItem);
    }

    public List<TraceItem> getData() {
        return data;
    }
}
