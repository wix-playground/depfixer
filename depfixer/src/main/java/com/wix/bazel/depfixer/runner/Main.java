package com.wix.bazel.depfixer.runner;

import com.wix.bazel.depfixer.DepFixer;
import com.wix.bazel.depfixer.brokentarget.BrokenTargetExtractor;
import com.wix.bazel.depfixer.brokentarget.BrokenTargetExtractorFactory;
import com.wix.bazel.depfixer.brokentarget.TextBrokenTargetExtractor;
import com.wix.bazel.depfixer.configuration.CliSource;
import com.wix.bazel.depfixer.configuration.Configuration;
import com.wix.bazel.depfixer.configuration.UserConfigSource;
import com.wix.bazel.depfixer.overrides.NoOverrides;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String... args) throws InterruptedException, ExecutionException, IOException {
        Configuration configuration = new Configuration(
                CliSource.parseCliParameters(args),
                new UserConfigSource()
        );

        BrokenTargetExtractorFactory brokenTargetExtractorFactory = new BrokenTargetExtractorFactory() {
            @Override
            public BrokenTargetExtractor create(Path repoPath, Path externalRepoPath, Path runPath, String stderr) {
                return new TextBrokenTargetExtractor(repoPath, externalRepoPath, stderr);
            }

            public List<String> initArgs(Path path, String targetsToBuild) {
                List<String> args = new ArrayList<>(Arrays.asList("-k", "--build_tag_filters=-deployable"));
                args.add(targetsToBuild);
                return args;
            }
        };

        new DepFixer(
                configuration,
                new NoOverrides(),
                null,
                brokenTargetExtractorFactory
        ).fix();
    }
}
