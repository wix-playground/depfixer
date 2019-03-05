package com.wix.bazel.analyze;

import java.util.regex.Pattern;

public class MissingSymbolAnalyzer extends AbstractRegexAnalyzer {
    private Pattern pattern =
            Pattern.compile("error: Symbol '[^\\s]+ ([^']+)' is missing from the classpath\\.");

    protected MissingSymbolAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
