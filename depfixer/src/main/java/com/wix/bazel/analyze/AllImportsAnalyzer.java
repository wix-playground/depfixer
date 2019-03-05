package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AllImportsAnalyzer extends AbstractTargetAnalyzer {
    private final Pattern importPattern = ImportAnalysis.importPattern;

    private final Pattern notFoundMember =
            Pattern.compile("(.+):\\d+: error: not found: (?:value|type|object) ([^\n]+)\n([^\n]+)");

    private final Pattern couldNotResolve =
            Pattern.compile("(.+):\\d+: error: could not resolve ([^\\s]+)");

    private Pattern cannotFindSymbol =
            Pattern.compile("(.+):\\d+: error: cannot find symbol\n[^\n]+\n\\s*\\^\n\\s*symbol:\\s+class ([^\n]+)"
                    , Pattern.MULTILINE);

    AllImportsAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Set<String> classes = new HashSet<>();

        Map<String, Set<String>> imports = getImports();
        Map<String, Set<String>> hints = getHints(targetData);

        AnalyzerResult result = new AnalyzerResult();

        for (Map.Entry<String, Set<String>> entry : hints.entrySet()) {
            Set<String> fileHints = entry.getValue();
            Set<String> fileImports = imports.get(entry.getKey());

            if (fileImports == null || fileImports.isEmpty())
                continue;

            Set<String> allImports =
                    fileImports.stream().filter(ImportAnalysis::isImportAll).collect(Collectors.toSet());

            for (String hint : fileHints) {
                Optional<String> maybeHintImport = fileImports.stream().filter(s -> s.endsWith("." + hint)).findAny();

                if (maybeHintImport.isPresent()) {
                    result.addClass(maybeHintImport.get(), 100, 60);
                    continue;
                }

                allImports.forEach(i ->
                        result.addClass(i.replaceFirst("[_*]", hint), 80, 60));
            }

            if (fileHints.isEmpty()) {
                allImports.forEach(i ->
                        result.addClass(i.replaceFirst("\\.[_*]", ""), 100, 60));
            }
        }

        if (hints.isEmpty()) {
            Set<String> importsFromStream =
                    RegexAnalysis.extractClasses(targetData.getStream(), importPattern).stream()
                    .flatMap(ImportAnalysis::processImportsStream)
                    .collect(Collectors.toSet());

            imports.values().stream()
                    .flatMap(Collection::stream)
                    .filter(ImportAnalysis::isImportAll)
                    .filter(importsFromStream::contains)
                    .forEach(i ->
                            result.addClass(i.replaceFirst("\\.[_*]", ""), 100, 60));
        }

        return result;
    }

    private Map<String, Set<String>> getImports() {
        Map<String, Set<String>> imports = new HashMap<>();
        for (Map.Entry<String, String> fileAndContent : ctx.getFileToContentMap().entrySet()) {
            Set<String> fileImports = extractImports(fileAndContent);
            imports.put(fileAndContent.getKey(), fileImports);
        }

        return imports;
    }

    private Set<String> extractImports(Map.Entry<String, String> fileAndContent) {
        return ImportAnalysis.extractImports(fileAndContent.getValue());
    }

    private Map<String, Set<String>> getHints(BrokenTargetData targetData) {
        Map<String, Set<String>> hints = new HashMap<>();

        mergeHintsWith(notFoundMember, targetData, hints);
        mergeHintsWith(couldNotResolve, targetData, hints);
        mergeHintsWith(cannotFindSymbol, targetData, hints);

        return hints;
    }

    private void mergeHintsWith(Pattern pattern, BrokenTargetData targetData, Map<String, Set<String>> hints) {
        for (Map.Entry<String, Set<String>> entry : RegexAnalysis.collectHints(targetData, pattern).entrySet()) {
            Set<String> fileHints = hints.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            fileHints.addAll(entry.getValue());
        }
    }
}
