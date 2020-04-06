package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class ClassfileNotFoundAnalyzer extends AbstractRegexAnalyzer {
    private static Pattern pattern =
            Pattern.compile("\\s*class file for ([^\\s]+) not found\\s*");

    protected ClassfileNotFoundAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
