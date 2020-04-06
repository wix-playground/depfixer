package com.wix.bazel.depfixer.analyze;

import java.util.regex.Pattern;

public class GetAllFqcnImportsAnalyzer extends AbstractImportRegexAnalyzer {
    private final Pattern pattern =
            Pattern.compile("\\s*import\\s*(?:static\\s*)?((?:[\\p{L}\\p{N}_$]*)(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)(?:\\.[*_])?);?");

    protected GetAllFqcnImportsAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
