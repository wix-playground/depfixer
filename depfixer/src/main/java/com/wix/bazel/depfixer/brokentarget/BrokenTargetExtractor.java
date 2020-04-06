package com.wix.bazel.depfixer.brokentarget;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BrokenTargetExtractor extends AbstractBrokenTargetExtractor {

    private final String stream;

    public BrokenTargetExtractor(Path repoPath, Path externalRepoPath, String stream) {
        super(repoPath, externalRepoPath);
        this.stream = stream;
    }

    public Collection<BrokenTargetData> collectBrokenTargets(Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(stream);

        List<BrokenTargetData> targets = new ArrayList<>();
        Set<String> targetNames = new HashSet<>();

        while (matcher.find()) {
            BrokenTargetData data = createBrokenTarget(stream, type, matcher);

            if (targetNames.add(data.targetName))
                targets.add(data);
        }

        return targets;
    }
}
