package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotFoundMemberAnalyzer extends AbstractTargetAnalyzer {
    private Pattern notFoundMember =
            Pattern.compile("error: not found: (?:value|type) ([^\n]+)\n([^\n]+)");

    protected NotFoundMemberAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Matcher matcher = notFoundMember.matcher(targetData.getStream());

        Set<String> classes = new HashSet<>();

        while (matcher.find()) {
            String value = matcher.group(1);
            String errorLine = matcher.group(2);

            if (errorLine.startsWith("import")) continue;

            Pattern fqn = Pattern.compile(".*(" + value + "(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)).*");
            Matcher fqnMatcher = fqn.matcher(errorLine);

            if (fqnMatcher.find()) {
                String fqnCls = fqnMatcher.group(1);
                classes.add(fqnCls);
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
