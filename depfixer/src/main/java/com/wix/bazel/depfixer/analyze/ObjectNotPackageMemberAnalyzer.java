package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectNotPackageMemberAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern objectNotPackageMember =
            Pattern.compile("error: (?:object|type) ([^\\s]+) is not a member of package ([^\\s]+)\n([^\n]+)");

    ObjectNotPackageMemberAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Set<String> classes = new HashSet<>();

        Matcher objectNotPackageMemberMatcher = objectNotPackageMember.matcher(targetData.getStream());

        while (objectNotPackageMemberMatcher.find()) {
            String cls = objectNotPackageMemberMatcher.group(1);
            String pck = objectNotPackageMemberMatcher.group(2);
            String errorLine = objectNotPackageMemberMatcher.group(3).trim();

            Set<String> hints;
            if (errorLine.startsWith("import")) {
                String aImport = errorLine
                        .replaceFirst("import\\s+", "")
                        .replace(";", "");

                hints = ImportAnalysis.processImport(aImport);
            } else {
                hints = Collections.singleton(errorLine);
            }

            for (String hint : hints) {
                Pattern fqn = Pattern.compile(".*(" + pck + "\\." + cls + "(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)).*");
                Matcher matcher = fqn.matcher(hint);

                if (matcher.find()) {
                    String fqnCls = matcher.group(1);
                    classes.add(fqnCls);
                } else if (hint.indexOf(cls) > -1) {
                    int idx = hint.indexOf(cls);

                    hint = hint.substring(idx);
                    matcher = ImportAnalysis.fqcn.matcher(pck + "." + hint);

                    if (matcher.find())
                        classes.add(matcher.group(0));
                    else
                        classes.add(pck + "." + cls);

                } else {
                    classes.add(pck + "." + cls);
                }
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }
}
