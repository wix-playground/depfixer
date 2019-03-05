package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CannotFindSymbolWithPackageAnalyzer extends AbstractTargetAnalyzer {
    private Pattern pattern =
            Pattern.compile("(.+):\\d+: error: cannot find symbol\n([^\n]+)\n\\s*\\^\n\\s*symbol:\\s+variable ([^\n]+)"
                    , Pattern.MULTILINE);

    private final Pattern packagePattern =
            Pattern.compile(("\\s*package\\s+([^\\s;]+)"));

    protected CannotFindSymbolWithPackageAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Matcher matcher = pattern.matcher(targetData.getStream());
        Set<String> classes = new HashSet<>();

        while (matcher.find()) {
            String srcFile = matcher.group(1);
            String errorLine = matcher.group(2);
            String value = matcher.group(3);

            if (value.equals("super")) {
                continue;
            }

            String content = ctx.getFileToContentMap().get(srcFile);

            Matcher packageMatcher = packagePattern.matcher(content);

            if (packageMatcher.find()) {
                String pack = packageMatcher.group(1);

                Pattern fqn = Pattern.compile(".*(" + value + "(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)).*");
                Matcher fqnMatcher = fqn.matcher(errorLine);

                if (fqnMatcher.find()) {
                    String fqnCls = fqnMatcher.group(1);
                    classes.add(pack + "." + fqnCls);
                } else {
                    classes.add(pack + "." + value);
                }
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
