package com.wix.bazel.analyze;

import java.util.regex.Pattern;

public class ClassNotFoundExceptionAnalyzer extends AbstractRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("java\\.lang\\.ClassNotFoundException: ([^\\s]+)");

    protected ClassNotFoundExceptionAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
