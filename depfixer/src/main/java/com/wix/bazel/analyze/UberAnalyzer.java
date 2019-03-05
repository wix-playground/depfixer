package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UberAnalyzer extends AbstractTargetAnalyzer {

    private final List<AbstractTargetAnalyzer> analyzers = new LinkedList<>();

    public UberAnalyzer(AnalyzerContext ctx) {
        super(ctx);

        analyzers.add(new ObjectNotPackageMemberAnalyzer(ctx));
        analyzers.add(new ClassNotFoundAnalyzer(ctx));
        analyzers.add(new ClassNotFoundExceptionAnalyzer(ctx));
        analyzers.add(new MissingSymbolAnalyzer(ctx));
        analyzers.add(new CouldNotResolveAnalyzer(ctx));
        analyzers.add(new SymbolNotFoundAnalyzer(ctx));
        analyzers.add(new ClassfileNotFoundAnalyzer(ctx));
        analyzers.add(new ImportNotFoundObjectAnalyzer(ctx));
        analyzers.add(new CannotFindSymbolAnalyzer(ctx));
        analyzers.add(new GetAllFqcnImportsAnalyzer(ctx));
        analyzers.add(new ScalaXmlAnalyzer(ctx));
        analyzers.add(new PackageDoesNotExistAnalyzer(ctx));
        analyzers.add(new AllImportsAnalyzer(ctx));
        analyzers.add(new NotFoundMemberAnalyzer(ctx));
        analyzers.add(new NotFoundValueWithPackageAnalyzer(ctx));
        analyzers.add(new PackageDoesNotExistExtendedAnalyzer(ctx));
        analyzers.add(new FqcnCouldNotResolveAnalyzer(ctx));
        analyzers.add(new WorkerLogAnalyzer(ctx));
        analyzers.add(new SymbolTypeMissingFromClasspathAnalyzer(ctx));
        analyzers.add(new TremNotFoundAnalyzer(ctx));
        analyzers.add(new CannotFindSymbolWithPackageAnalyzer(ctx));
        analyzers.add(new NeedsToBeAbstractAnalyzer(ctx));
        analyzers.add(new UnableToLocatClassAnalyzer(ctx));
        analyzers.add(new ToolInfoAnalyzer(ctx));
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        return analyzers
                .stream()
                .filter(TargetAnalyzer::isEnabled)
                .map(a -> a.analyze(targetData)).reduce(new AnalyzerResult(), AnalyzerResult::merge);
    }
}
