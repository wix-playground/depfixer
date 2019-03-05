package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

public class ScalaXmlAnalyzer extends AbstractTargetAnalyzer {
    protected ScalaXmlAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        AnalyzerResult result = new AnalyzerResult();

        if (targetData.getStream().contains("scala.xml package must be on the classpath"))
            result.addClass("scala.xml", 100);

        return result;
    }
}
