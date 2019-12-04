package com.wix.bazel.brokentarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract public class AbstractBrokenTargetExtractor {
    private static Pattern failingFileAndTarget_K =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file .+\\.jar: scala(?:\\sdeployable)? (.+) failed");

    private static Pattern failingFileAndTargetJava =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file ([^\\s]+)_(?:java|java-hjar)\\.jar[^\n]+");

    private static Pattern failingFileAndTargetProto =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file .+ creating scalapb files ([^\\s]+)_srcjar[^\n]+");

    private static Pattern failingFileAndTargetJavaTest =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file ([^\\s]+)\\.jar: Building ([^\\s]+)\\.jar[^\n]+");


    private final Path repoPath, externalRepoPath;


    AbstractBrokenTargetExtractor(Path repoPath, Path externalRepoPath) {
        this.repoPath = repoPath;
        this.externalRepoPath = externalRepoPath;
    }

    public List<BrokenTargetData> extract() throws IOException {
        Collection<BrokenTargetData> scalaTargets = collectBrokenTargets(failingFileAndTarget_K, "scala");
        Collection<BrokenTargetData> javaTargets = collectBrokenTargets(failingFileAndTargetJava, "java");
        Collection<BrokenTargetData> protoTargets = collectBrokenTargets(failingFileAndTargetProto, "proto");
        Collection<BrokenTargetData> javaTestTargets = collectBrokenTargets(failingFileAndTargetJavaTest, "java_test");

        Set<BrokenTargetData> allJavaTargets = new HashSet<>(javaTargets);
        allJavaTargets.addAll(javaTestTargets);

        List<BrokenTargetData> allTargets = new ArrayList<>(scalaTargets);
        allTargets.addAll(allJavaTargets);
        allTargets.addAll(protoTargets);
        allTargets.sort(Comparator.comparingInt(x -> x.start));

        for (int i = 0; i < allTargets.size() - 1; i++) {
            allTargets.get(i).end = allTargets.get(i + 1).errorStart;
        }

        return allTargets;
    }


    abstract Collection<BrokenTargetData> collectBrokenTargets(Pattern pattern, String type) throws IOException;

    protected BrokenTargetData createBrokenTarget(String stream, String type, Matcher matcher) {
        BrokenTargetData data = new BrokenTargetData();
        data.targetName = matcher.group(1);
        data.targetName = data.targetName.split("\\s",2)[0];

        System.out.println("Found target: " + data.targetName);

        data.errorStart = matcher.start();
        data.start = matcher.end();
        data.end = stream.length();
        data.fullStream = stream;
        data.type = type;

        if (!type.equals("proto")) {
            populateTargetName(data);
        }

        if (data.targetName.startsWith("@")) {
            data.external = true;
            data.repoName = data.targetName.split("//")[0].substring(1);
        } else {
            data.repoName = "//";
        }
        return data;
    }

    private void populateTargetName(BrokenTargetData data) {
        Path workspacePath = repoPath;

        if (data.targetName.startsWith("external/")) {
            System.out.println("Target " + data.targetName + " is external");
            data.targetName = data.targetName.replace("external/", "");
            workspacePath = externalRepoPath;
            data.external = true;
        }

        if (!data.targetName.startsWith("//") && !data.targetName.startsWith("@")) {
            String[] targetParts = data.targetName.split("/");
            Path targetPath = null;
            for(String part : targetParts) {
                Path aggregatedPath;

                if (targetPath == null) {
                    aggregatedPath = Paths.get(part);
                } else {
                    aggregatedPath = targetPath.resolve(part);
                }

                Path path = workspacePath.resolve(aggregatedPath);

                if (Files.isDirectory(path)) {
                    targetPath = aggregatedPath;
                } else {
                    break;
                }
            }

            System.out.println("Target " + data.targetName + " path is: " + targetPath);

            assert targetPath != null;
            String ruleName = targetPath.relativize(Paths.get(data.targetName)).toString();

            if ((ruleName.startsWith("lib") || ruleName.endsWith("_java") || ruleName.endsWith("java-hjar")) &&
                    data.type.equals("java_test")) {
                Path buildFile = repoPath.resolve(targetPath.resolve("BUILD.bazel"));

                if (!Files.isRegularFile(buildFile)) {
                    buildFile = repoPath.resolve(targetPath.resolve("BUILD"));
                }

                try {
                    String content = new String(Files.readAllBytes(buildFile));
                    if (!content.contains("\"" + ruleName + "\"")) {
                        ruleName = ruleName.replace("lib", "")
                                .replace("java-hjar", "")
                                .replace("_java", "");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            data.targetName = (data.external ? "@" : "//") + targetPath.toString() + ":" + ruleName;

            if (data.external) {
                data.targetName = data.targetName.replaceFirst("/", "//");
            }
        }

        if (data.targetName.endsWith("_generator")) {
            data.targetName = data.targetName.substring(0, data.targetName.length() - "_generator".length());
        }
    }

}
