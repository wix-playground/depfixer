package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractImportRegexAnalyzer extends AbstractRegexAnalyzer {

    protected AbstractImportRegexAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Set<String> extractClasses(BrokenTargetData targetData) {
        return super.extractClasses(targetData).stream()
                .flatMap(x -> ImportAnalysis.processImport(x).stream())
                .collect(Collectors.toSet());
    }
}
