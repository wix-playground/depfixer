package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SymbolTypeMissingFromClasspathAnalyzer extends AbstractTargetAnalyzer {

    private static Pattern pattern =
            Pattern.compile("error: Symbol 'type ([^\\s]+)' is missing from the classpath\\.");

    protected SymbolTypeMissingFromClasspathAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Set<String> classesFromTarget = RegexAnalysis.extractClasses(targetData.getStream(), pattern);

        Set<String> classes = classesFromTarget.stream().map(this::processType).collect(Collectors.toSet());

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 60, 40);

        return result;
    }

    private String processType(String cls) {
        int idx = cls.indexOf(".package");

        if (idx > 0) {
            return cls.substring(0, idx + ".package".length());
        }

        return cls;
    }
}
