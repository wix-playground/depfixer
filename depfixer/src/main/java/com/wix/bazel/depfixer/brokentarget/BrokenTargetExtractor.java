package com.wix.bazel.depfixer.brokentarget;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface BrokenTargetExtractor {
    List<BrokenTargetData> extract() throws IOException;
}
