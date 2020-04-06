package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class PackageDoesNotExistAnalyzer extends AbstractImportRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: package [^\\s]+ does not exist\n\\s*import\\s+([^\\s;]+)");

    protected PackageDoesNotExistAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
