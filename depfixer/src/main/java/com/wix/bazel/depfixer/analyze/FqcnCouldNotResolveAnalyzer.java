package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FqcnCouldNotResolveAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern pattern =
            Pattern.compile("(.+):\\d+: error: could not resolve ([^\\s]+)");

    private final Pattern packagePattern =
            Pattern.compile(("\\s*package\\s+([^\\s;]+)"));

    protected FqcnCouldNotResolveAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Matcher matcher = pattern.matcher(targetData.getStream());
        Set<String> classes = new HashSet<>();

        Map<String, Set<String>> imports = getImports();

        while (matcher.find()) {
            String srcFile = matcher.group(1);
            String line = matcher.group(2);

            Pattern fqn = Pattern.compile("(?:[\\p{L}_$][\\p{L}\\p{N}_$]*)(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)");
            Matcher fqcnMatcher = fqn.matcher(line);

            boolean found = false;
            while (fqcnMatcher.find()) {
                classes.add(fqcnMatcher.group(0));
                found = true;
            }

            if (!found) {
                String content = ctx.getFileToContentMap().get(srcFile);

                Matcher packageMatcher = packagePattern.matcher(content);

                if (packageMatcher.find()) {
                    Set<String> fileImports = imports.get(srcFile);
                    Optional<String> maybeHintImport = fileImports.stream().filter(
                            s -> s.endsWith("." + line) || s.contains("." + line + ".")).findAny();

                    if (maybeHintImport.isPresent()) {
                        continue;
                    }

                    String pack = packageMatcher.group(1);
                    classes.add(pack + "." + line);

                }
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }

    private Map<String, Set<String>> getImports() {
        Map<String, Set<String>> imports = new HashMap<>();
        for (Map.Entry<String, String> fileAndContent : ctx.getFileToContentMap().entrySet()) {
            Set<String> fileImports = ImportAnalysis.extractImports(fileAndContent.getValue());
            imports.put(fileAndContent.getKey(), fileImports);
        }

        return imports;
    }
}
