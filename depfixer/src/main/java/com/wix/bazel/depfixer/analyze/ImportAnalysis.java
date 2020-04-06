package com.wix.bazel.depfixer.analyze;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface ImportAnalysis {
    Pattern importPattern =
            Pattern.compile("\\s*import\\s+(?:static\\s+)?([^\n;]+)");

    Pattern fqcn =
            Pattern.compile("(?:[a-z\\p{N}_$]*)(?:\\.[a-z_$][\\p{L}\\p{N}_$]*)*(?:\\.[A-Z_$][\\p{L}\\p{N}_$]*)");

    Pattern pack =
            Pattern.compile("(?:[a-z\\p{N}_$]*)(?:\\.[a-z_$][\\p{L}\\p{N}_$]*)*(?:\\.package)");

    static Stream<String> processImportsStream(String aImport) {
        return processImport(aImport).stream();
    }

    static Set<String> processImport(String aImport) {
        aImport = aImport.replace("`", "");
        Set<String> processedImports = new HashSet<>();

        LinkedList<String> stack = new LinkedList<>(Collections.singleton(aImport));

        while(!stack.isEmpty()) {
            String currentImport = stack.removeLast();

            int multIdx = currentImport.indexOf('{');
            if (multIdx > -1) {
                String importPackage = currentImport.substring(0, multIdx);
                String[] importedClasses;

                try {
                    importedClasses =
                        currentImport.substring(multIdx + 1, currentImport.length() - 1).split("\\s*,\\s*");
                } catch (StringIndexOutOfBoundsException e) {
                    continue;
                }

                for(String cls: importedClasses) {
                    String importClass = importPackage + cls;
                    importClass = importClass.split("\\s*(?:=>|⇒)\\s*")[0];

                    processedImports.add(importClass);
                }
            } else {
                processedImports.add(aImport);
            }
        }

        return processedImports;
    }

    static Set<String> extractImports(String from) {
        Matcher matcher = importPattern.matcher(from);

        Set<String> imports = new HashSet<>();

        while (matcher.find()) {
            String aImport = matcher.group(1);
            imports.addAll(ImportAnalysis.processImport(aImport));
        }

        return imports;
    }

    static boolean isImportAll(String aImport) {
        return aImport.endsWith("._") || aImport.endsWith(".*");
    }

    static boolean isFqcn(String aImport) {
        return fqcn.matcher(aImport).matches();
    }

    static Optional<String> fqcn(String aImport) {
        Matcher matcher = fqcn.matcher(aImport);

        if (matcher.find()) {
            return Optional.of(matcher.group());
        }

        return Optional.empty();
    }

    static Optional<String> packageImport(String aImport) {
        Matcher matcher = pack.matcher(aImport);

        if (matcher.find()) {
            return Optional.of(matcher.group());
        }

        return Optional.empty();
    }
}
