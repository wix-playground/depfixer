package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractRegexAnalyzer extends AbstractTargetAnalyzer {
    protected AbstractRegexAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    protected abstract Pattern getPattern();

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        AnalyzerResult result = new AnalyzerResult();
        Set<String> classes = extractClasses(targetData);
        result.addClasses(classes, 100, 60);

        return result;
    }

    protected Set<String> extractClasses(BrokenTargetData targetData) {
        Matcher matcher = getPattern().matcher(targetData.getStream());

        Set<String> classes = new HashSet<>();

        while (matcher.find()) {
            String className = extractClassName(matcher);
            classes.add(className);
        }

        return classes;
    }


    protected String extractClassName(Matcher matcher) {
        return matcher.group(1);
    }
}
