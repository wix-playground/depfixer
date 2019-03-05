package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NeedsToBeAbstractAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern needsToBeAbstract =
            Pattern.compile("error: class [\\S]+ needs to be abstract, ([^\n]+)");
    private final Pattern fqcnPattern =
            Pattern.compile("(?:[\\p{L}_$][\\p{L}\\p{N}_$]*)(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)");

    protected NeedsToBeAbstractAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Set<String> lines = RegexAnalysis.extractClasses(targetData.getStream(), needsToBeAbstract);

        Set<String> classes = lines.stream()
                .flatMap(l -> RegexAnalysis.extractClasses(l, fqcnPattern, m -> m.group(0)).stream())
                .collect(Collectors.toSet());

        if (classes.isEmpty() && !lines.isEmpty()) {
            classes = RegexAnalysis.extractClasses(targetData.getStream(), fqcnPattern, m -> m.group(0));
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
