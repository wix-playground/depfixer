package com.wix.bazel.depfixer.brokentarget;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.wixpress.ci.bazel.bep.processor.BuildEventProtocolDeserializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrokenTargetBepExtractor extends AbstractBrokenTargetExtractor {
    public static String BEP_FILENAME = "build.bep";
    private final Path runPath;
    private BuildEventProtocolDeserializer buildEventProtocolDeserializer = new BuildEventProtocolDeserializer();


    public BrokenTargetBepExtractor(Path repoPath, Path externalRepoPath, Path runPath) {
        super(repoPath, externalRepoPath);
        this.runPath = runPath;
    }

    public Collection<BrokenTargetData> collectBrokenTargets(Pattern pattern, String type) throws IOException {
        Map<String, BrokenTargetData> targets = new HashMap<>();
        List<BuildEvent> bepEvents = new ArrayList<>();

        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = mapper.getFactory().createParser(runPath.resolve(BEP_FILENAME).toFile());
        while(parser.nextToken() == JsonToken.START_OBJECT) {
            ObjectNode node = mapper.readTree(parser);
            if(containsStdoutOrStderr(node)) {
                bepEvents.add(buildEventProtocolDeserializer.read(node.toString()));
            }
        }

        for (BuildEvent bepEvent : bepEvents) {
            Matcher matcher = pattern.matcher(bepEvent.getProgress().stderr());
            while (matcher.find()) {
                String fullStream = bepEvent.getProgress().stderr() + "\n" + bepEvent.getProgress().stdout();
                BrokenTargetData brokenTarget = createBrokenTarget(fullStream, type, matcher);
                brokenTarget.fullStream = fullStream;
                brokenTarget.fromBep = true;

                BrokenTargetData target = targets.computeIfPresent(brokenTarget.getName(), (key, existingTarget) -> {
                    existingTarget.fullStream += "\n" + brokenTarget.getStream(); //aggregate multiple streams for the same broken target
                    return existingTarget;
                });
                if (target == null) {
                    targets.put(brokenTarget.getName(), brokenTarget);
                }
            }
        }
        return targets.values();
    }

    private boolean containsStdoutOrStderr(ObjectNode node) {
        return node.path("progress").has("stdout") ||  node.path("progress").has("stderr");
    }
}
