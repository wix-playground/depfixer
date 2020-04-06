package com.wix.bazel.depfixer.analyze;

import java.util.*;

public class AnalyzerResult {
    private final Map<Integer, Set<String>> classesByScoreMap = new HashMap<>();

    public void addClass(String cls, int score) {
        Set<String> scoreClasses = classesByScoreMap.computeIfAbsent(score, s -> new HashSet<>());
        scoreClasses.add(cls);
    }

    public void addClass(String cls) {
        addClass(cls, 100, 80);
    }

    public void addClass(String cls, int fqnScore, int notFqnScore) {
        Optional<String> maybeFqn = ImportAnalysis.fqcn(cls);

        if (maybeFqn.isPresent()) {
            String fqn = maybeFqn.get();
            if (fqn.endsWith("._")) {
                addClass(fqn.substring(0, fqn.length() - 1) + "package", notFqnScore);
            } else {
                addClass(fqn, fqnScore);
            }
        } else {
            addClass(cls, notFqnScore);
        }
    }

    public void addClasses(Set<String> classes, int fqnScore, int notFqnScore) {
        classes.forEach(c -> addClass(c, fqnScore, notFqnScore));
    }

    private void addClasses(int score, Set<String> classes) {
        if (classes.isEmpty()) {
            return;
        }

        Set<String> scoreClasses = classesByScoreMap.computeIfAbsent(score, s -> new HashSet<>());
        scoreClasses.addAll(classes);
    }

    public AnalyzerResult merge(AnalyzerResult result) {
        result.classesByScoreMap.forEach(this::addClasses);

        return this;
    }

    public Set<String> removeHighestScore() {
        Optional<Integer> highestScore = classesByScoreMap.keySet()
                .stream()
                .max(Integer::compareTo);

        if (highestScore.isPresent()) {
            return classesByScoreMap.remove(highestScore.get());
        }

        return Collections.emptySet();
    }

    public boolean isNotEmpty() {
        return !classesByScoreMap.isEmpty();
    }
}
