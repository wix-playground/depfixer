package com.wix.bazel.analyze;

import java.util.regex.Pattern;

public class ValueIsNotMemberOfObjectAnalyzer extends AbstractRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: value (?:[^\\s]+) is not a member of object ([^\\s]+)");

    protected ValueIsNotMemberOfObjectAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
