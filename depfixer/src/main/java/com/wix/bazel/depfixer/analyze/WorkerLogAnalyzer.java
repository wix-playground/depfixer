package com.wix.bazel.depfixer.analyze;

import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkerLogAnalyzer extends AbstractTargetAnalyzer {
    private static Pattern pattern =
            Pattern.compile("Start of log snippet, file at ([^\\s]+)");

    private static Pattern noClassDefFoundErrorPattern =
            Pattern.compile("java\\.lang\\.NoClassDefFoundError: ([^\\s]+)");

    private static Pattern treePattern =
            Pattern.compile("tree\\s+=\\sApply:<([^:]+): error");

    private static Pattern srcFilePattern =
            Pattern.compile("while compiling: ([^\\n]+)");

    protected WorkerLogAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        Matcher matcher = pattern.matcher(targetData.getStream());
        Set<String> classes = new HashSet<>();

        while (matcher.find()) {
            String logFile = matcher.group(1);
            String workerLogContent = null;
            try {
                workerLogContent = new String(Files.readAllBytes(Paths.get(logFile)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Matcher clsMatcher = noClassDefFoundErrorPattern.matcher(workerLogContent);

            while (clsMatcher.find()) {
                String cls = clsMatcher.group(1).replace("/", ".");
                classes.add(cls);
            }

            if (classes.isEmpty()) {
                Matcher treeMatcher = treePattern.matcher(workerLogContent);

                while(treeMatcher.find()) {
                    String cls = treeMatcher.group(1);
                    String fileContent = getFileContent(targetData, workerLogContent);

                    Pattern treeClassPattern =
                            Pattern.compile("\\s*import\\s+((?:[\\p{L}\\p{N}_$]*)(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*\\." + cls + ")");

                    Matcher fqnMatcher = treeClassPattern.matcher(fileContent);
                    if (fqnMatcher.find()) {
                        classes.add(fqnMatcher.group(1));
                    }
                }
            }
        }

        AnalyzerResult result = new AnalyzerResult();
        result.addClasses(classes, 100, 60);

        return result;
    }

    private String getFileContent(BrokenTargetData targetData, String workerLogContent) {
        Matcher srcFileMatcher = srcFilePattern.matcher(workerLogContent);

        if (srcFileMatcher.find()) {
            String relativeSrcFile = srcFileMatcher.group(1);

            Path srcFilePath;
            if (targetData.isExternal()) {
                srcFilePath = ctx.getBazelExternalPath().resolve(relativeSrcFile);
            } else {
                srcFilePath = ctx.getRepoPath().resolve(relativeSrcFile);
            }

            try {
                return new String(Files.readAllBytes(srcFilePath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("Worker log analyzer error - cannot find src file");
    }
}
