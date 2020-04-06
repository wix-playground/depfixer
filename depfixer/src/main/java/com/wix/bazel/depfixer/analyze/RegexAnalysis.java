package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RegexAnalysis {
    static Set<String> extractClasses(String content, Pattern pattern) {
        return extractClasses(content, pattern, m -> m.group(1));
    }

    static Set<String> extractClasses(String content, Pattern pattern, Function<Matcher, String> classExtractor) {
        Matcher matcher = pattern.matcher(content);

        Set<String> classes = new HashSet<>();

        while(matcher.find()) {
            String className = classExtractor.apply(matcher);
            classes.add(className);
        }
        return classes;
    }

    static Map<String, Set<String>> collectHints(BrokenTargetData targetData, Pattern pattern) {
        return collectHints(targetData, pattern, m -> m.group(2), m -> m.group(1));
    }

    static Map<String, Set<String>> collectHints(BrokenTargetData targetData, Pattern pattern,
                                                 Function<Matcher, String> hintExtractor, Function<Matcher, String> fileExtractor) {
        Matcher matcher = pattern.matcher(targetData.getStream());

        Map<String, Set<String>> hints = new HashMap<>();

        while(matcher.find()) {
            String file = fileExtractor.apply(matcher);
            String hint = hintExtractor.apply(matcher);

            Set<String> fileHints = hints.computeIfAbsent(file, x -> new HashSet<>());
            fileHints.add(hint);
        }

        return hints;
    }
}
