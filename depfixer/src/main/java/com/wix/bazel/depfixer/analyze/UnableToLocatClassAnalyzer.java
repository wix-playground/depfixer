package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class UnableToLocatClassAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern unableToLocate =
            Pattern.compile("error: Unable to locate class ([^\n]+)");
    private final Pattern fqcnPattern =
            Pattern.compile("(?:[\\p{L}_$][\\p{L}\\p{N}_$]*)(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)");

    protected UnableToLocatClassAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Set<String> lines = RegexAnalysis.extractClasses(targetData.getStream(), unableToLocate);

        Set<String> classes = lines.stream()
                .flatMap(l -> RegexAnalysis.extractClasses(l, fqcnPattern, m -> m.group(0)).stream())
                .collect(Collectors.toSet());

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
