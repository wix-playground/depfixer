package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ToolInfoAnalyzer extends AbstractRegexAnalyzer {
    private final Pattern pattern = Pattern.compile("\\[strict] Using type ([^\\s]+)\\$? from an indirect dependency");

    protected ToolInfoAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    protected Set<String> extractClasses(BrokenTargetData targetData) {
        Set<String> classes = super.extractClasses(targetData);
        return classes.stream().map(this::fixClass).collect(Collectors.toSet());
    }

    private String fixClass(String cls) {
        if (cls.endsWith("$")) {
            return cls.substring(0, cls.length() - 1);
        }

        return cls;
    }

    @Override
    protected Pattern getPattern() {
        return pattern;
    }
}
