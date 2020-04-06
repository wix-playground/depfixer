package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotFoundValueWithPackageAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern pattern =
            Pattern.compile("(.+):\\d+: error: not found: (?:value|type|object) ([^\n]+)\n([^\n]+)");

    private final Pattern packagePattern =
            Pattern.compile(("\\s*package\\s+([^\\s;]+)"));

    private final Pattern importPattern =
            Pattern.compile("\\s*import\\s+(?:static\\s+)?([^\n;]+)");

    NotFoundValueWithPackageAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Map<String, Set<String>> hints = RegexAnalysis.collectHints(targetData, pattern, this::extractHint, m -> m.group(1));

        Set<String> hintsWithImports = new HashSet<>();

        AnalyzerResult result = new AnalyzerResult();

        for (Map.Entry<String, Set<String>> entry : hints.entrySet()) {
            Set<String> imports = extractImports(entry.getKey());

            for(String hint : entry.getValue()) {
                String[] hintParts = hint.split("\\.");

                StringBuilder agg = new StringBuilder();
                for (String part : hintParts) {
                    agg.append(".").append(part);

                    Optional<String> matchingImport = imports.stream().filter(i -> i.endsWith(agg.toString())).findFirst();

                    if (matchingImport.isPresent()) {

                        if (ImportAnalysis.isFqcn(matchingImport.get())) {
                            result.addClass(matchingImport.get(), 100, 60);
                        } else {
                            String reminder = hint.replace(agg.toString().substring(1), "");
                            result.addClass(matchingImport.get() + reminder, 80, 60);
                        }

                        hintsWithImports.add(hint);
                        break;
                    }
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : hints.entrySet()) {
            String srcFile = entry.getKey();

            for(String hint : entry.getValue()) {

                if (hintsWithImports.contains(hint)) {
                    continue; //hint has an import - no need to created noise by attaching it package
                }

                String content = ctx.getFileToContentMap().get(srcFile);
                Matcher packageMatcher = packagePattern.matcher(content);

                if (packageMatcher.find()) {
                    String pack = packageMatcher.group(1);
                    result.addClass(pack + "." + hint, 60, 40);
                }
            }
        }

        return result;
    }

    private String extractHint(Matcher matcher) {
        String value = matcher.group(2);
        String errorLine = matcher.group(3);

        Pattern fqn = Pattern.compile(".*(" + value + "(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)).*");
        Matcher fqnMatcher = fqn.matcher(errorLine);

        if (fqnMatcher.find()) {
            return fqnMatcher.group(1);
        } else {
            return value;
        }
    }

    private Set<String> extractImports(String fileName) {
        Matcher matcher = importPattern.matcher(ctx.getFileToContentMap().get(fileName));

        Set<String> imports = new HashSet<>();

        while (matcher.find()) {
            String aImport = matcher.group(1);
            imports.addAll(ImportAnalysis.processImport(aImport));
        }

        return imports;
    }
}
