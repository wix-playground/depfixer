package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class ClassNotFoundAnalyzer extends AbstractRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: Class ([^\\s]+) not found - continuing with a stub\\.");

    protected ClassNotFoundAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
