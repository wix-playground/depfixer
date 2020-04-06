package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class CouldNotResolveAnalyzer extends AbstractRegexAnalyzer {
    private Pattern pattern =
            Pattern.compile("error: Symbol '[^\\s]+ ([^']+)' is missing from the classpath\\.");

    protected CouldNotResolveAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
