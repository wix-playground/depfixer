package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageDoesNotExistExtendedAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern pattern =
            Pattern.compile("error: package ([^\\s]+) does not exist\n([^\\n]+)");

    protected PackageDoesNotExistExtendedAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Matcher matcher = pattern.matcher(targetData.getStream());
        Set<String> classes = new HashSet<>();

        while (matcher.find()) {
            String packgPrefix = matcher.group(1);
            String example = matcher.group(2);

            Pattern fqn = Pattern.compile(".*(" + packgPrefix + "(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)).*");
            Matcher fqnMatcher = fqn.matcher(example);

            while (fqnMatcher.find()) {
                String group = fqnMatcher.group(1);
                classes.add(group);
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
