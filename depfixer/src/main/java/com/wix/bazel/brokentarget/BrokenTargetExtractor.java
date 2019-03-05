package com.wix.bazel.brokentarget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrokenTargetExtractor {
    private static Pattern failingFileAndTarget_K =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file .+\\.jar: scala(?:\\sdeployable)? (.+) failed");

    private static Pattern failingFileAndTargetJava =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file ([^\\s]+)_(?:java|java-hjar)\\.jar[^\n]+");

    private static Pattern failingFileAndTargetProto =
            Pattern.compile("ERROR: (?:[^:]+):\\d+:\\d+: Couldn't build file .+ creating scalapb files ([^\\s]+)_srcjar[^\n]+");

    private final Path repoPath, externalRepoPath;

    public BrokenTargetExtractor(Path repoPath, Path externalRepoPath) {
        this.repoPath = repoPath;
        this.externalRepoPath = externalRepoPath;
    }

    public List<BrokenTargetData> extract(String stream) {
        return collectBrokenTargets(stream);
    }

    private List<BrokenTargetData> collectBrokenTargets(String stream) {
        List<BrokenTargetData> scalaTargets = collectBrokenTargets(stream, failingFileAndTarget_K, "scala");
        List<BrokenTargetData> javaTargets = collectBrokenTargets(stream, failingFileAndTargetJava, "java");
        List<BrokenTargetData> protoTargets = collectBrokenTargets(stream, failingFileAndTargetProto, "proto");

        List<BrokenTargetData> allTargets = new ArrayList<>(scalaTargets);
        allTargets.addAll(javaTargets);
        allTargets.addAll(protoTargets);
        allTargets.sort(Comparator.comparingInt(x -> x.start));

        for(int i = 0; i < allTargets.size() - 1; i++) {
            allTargets.get(i).end = allTargets.get(i + 1).errorStart;
        }

        return allTargets;
    }

    private List<BrokenTargetData> collectBrokenTargets(String stream, Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(stream);
        List<BrokenTargetData> targets = new ArrayList<>();

        while (matcher.find()) {
            BrokenTargetData data = new BrokenTargetData();
            data.targetName = matcher.group(1);

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

            targets.add(data);
        }

        return targets;
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
            data.targetName = (data.external ? "@" : "//") + targetPath.toString() + ":" +
                    targetPath.relativize(Paths.get(data.targetName));

            if (data.external) {
                data.targetName = data.targetName.replaceFirst("/", "//");
            }
        }

        if (data.targetName.endsWith("_generator")) {
            data.targetName = data.targetName.substring(0, data.targetName.length() - "_generator".length());
        }
    }
}
