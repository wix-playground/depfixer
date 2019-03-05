package com.wix.bazel.analyze;

import java.util.regex.Pattern;

public class ImportNotFoundObjectAnalyzer extends AbstractImportRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: (?:not found: )?object [^\n]+\n\\s*import ([^\n;]+)");

    protected ImportNotFoundObjectAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
