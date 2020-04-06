package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class SymbolNotFoundAnalyzer extends AbstractRegexAnalyzer {
    private static Pattern pattern =
            Pattern.compile("error: symbol not found ([^\\s]+)");

    protected SymbolNotFoundAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
