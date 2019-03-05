package com.wix.bazel.analyze;

import java.util.regex.Pattern;

public class CannotFindSymbolAnalyzer extends AbstractImportRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: cannot find symbol\n\\s*import\\s+(?:static\\s+)?([^\n;]+)", Pattern.MULTILINE);

    protected CannotFindSymbolAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
