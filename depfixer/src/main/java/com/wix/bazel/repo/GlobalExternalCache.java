package com.wix.bazel.repo;

import com.wix.bazel.process.RunWithRetries;
import com.wixpress.ci.labeldex.domain.SymbolType;
import com.wixpress.ci.labeldex.model.Bulk2ndPartyLabels;
import com.wixpress.ci.labeldex.model.Bulk3rdPartyLabels;
import com.wixpress.ci.labeldex.model.BulkLabels;
import com.wixpress.ci.labeldex.model.BulkLabelsSocialMode;
import com.wixpress.ci.labeldex.model.BulkSymbols;
import com.wixpress.ci.labeldex.model.SymbolLabels;
import com.wixpress.ci.labeldex.model.Symbol2ndPartyLabels;
import com.wixpress.ci.labeldex.model.Symbol3rdPartyLabels;
import com.wixpress.ci.labeldex.model.SecondPartyLabel;
import com.wixpress.ci.labeldex.model.ThirdPartyLabel;
import com.wixpress.ci.labeldex.model.Label;
import com.wixpress.ci.labeldex.restclient.LabeldexRestClient;
import com.wixpress.ci.rest.client.RestClient;
import scala.Enumeration;
import scala.collection.JavaConverters;
import scala.collection.mutable.Buffer;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalExternalCache {
    private final LabeldexRestClient labeldexrestClient = new LabeldexRestClient(
            new RestClient("http://bo.wix.com/labeldex-webapp", 3000));

    private final String srcWorkspaceName;
    private RepoCache cache;
    private final boolean cleanMode;



    public GlobalExternalCache(Set<String> tetOnlyTargets, String srcWorkspaceName, boolean cleanMode) {
        this.cache = new RepoCache(tetOnlyTargets);
        this.srcWorkspaceName = srcWorkspaceName;
        this.cleanMode = cleanMode;
    }

    public RepoCache get(List<String> classes) {
        List<String> filteredClasses = classes.stream()
                .filter(x -> !cache.getClasses().contains(x))
                .collect(Collectors.toList());

        if (filteredClasses.isEmpty()) {
            return cache;
        }

        getBulkLabelsAndAddToCache(filteredClasses);

        return cache;
    }

    private void getBulkLabelsAndAddToCache(List<String> classes) {
        BulkSymbols symbols = new BulkSymbols(JavaConverters.asScalaBuffer(classes).toSet());
        BulkLabelsSocialMode clientLabels =
                RunWithRetries.run(5, 500L,
                        () -> labeldexrestClient.findSocialBulkLabels(symbols,
                                srcWorkspaceName, true)
                );

        if (clientLabels == null) {
            return;
        }

        BulkLabels bazelLabels = clientLabels.bazelLabels();
        Bulk2ndPartyLabels bulk2ndPartyLabels = clientLabels.secondPartyLabels();
        Bulk3rdPartyLabels bulk3rdPartyLabels = clientLabels.thirdPartyLabels();

        List<SymbolLabels> symbolLabels = filterPackages(bazelLabels.symbolDocument().toBuffer(),
                s -> s.symbol().symbolType());

        List<Symbol2ndPartyLabels> symbol2ndPartyLabels = filterPackages(bulk2ndPartyLabels.symbolDocument().toBuffer(),
                s -> s.symbol().symbolType());

        List<Symbol3rdPartyLabels> symbol3rdPartyLabels = filterPackages(bulk3rdPartyLabels.symbolDocument().toBuffer(),
                s -> s.symbol().symbolType());

        Map<String, Set<String>> bazelLabelsMap = toLabelsMap(symbolLabels,
                s -> s.symbol().fullyQualifiedName(),
                s -> symbolLabels(s).map(this::formatLabel).collect(Collectors.toSet()));

        Map<String, Set<String>> symbol2ndPartyLabelsMap = toLabelsMap(symbol2ndPartyLabels,
                s -> s.symbol().fullyQualifiedName(),
                s -> JavaConverters.setAsJavaSet(s.labels()).stream()
                        .map(SecondPartyLabel::label).collect(Collectors.toSet()));

        Map<String, Set<String>> symbol3rdPartyLabelsMap = toLabelsMap(symbol3rdPartyLabels,
                s -> s.symbol().fullyQualifiedName(),
                s -> JavaConverters.setAsJavaSet(s.labels()).stream()
                        .map(ThirdPartyLabel::label).collect(Collectors.toSet()));

        for (String fqn : classes) {
            Optional<Set<String>> maybeLabels =
                    Stream.of(bazelLabelsMap, symbol3rdPartyLabelsMap, symbol2ndPartyLabelsMap)
                            .map(x -> x.get(fqn))
                            .filter(Objects::nonNull)
                            .findFirst();

            maybeLabels
                    .ifPresent(strings ->
                            strings.stream().filter(Targets::notExcluded)
                            .forEach(l -> cache.put(null, fqn, l)));
        }
    }

    private Stream<Label> symbolLabels(SymbolLabels labels) {
        Supplier<Stream<Label>> labelsStreamSupplier = () -> JavaConverters.setAsJavaSet(labels.labels()).stream();

        Supplier<Stream<Label>> fwLabelsStreamSupplier = () ->
                labelsStreamSupplier.get().filter(l -> l.workspace().equals("wix_platform_wix_framework"));

        Stream<Label> labelsStream;

        if (fwLabelsStreamSupplier.get().findAny().isPresent()) {
            labelsStream = fwLabelsStreamSupplier.get();
        } else {
            labelsStream = labelsStreamSupplier.get();
        }

        return labelsStream;
    }

    private String formatLabel(Label l) {
        if (l.workspace().equals(srcWorkspaceName))
            return fixLabel(l.label());

        return String.format("@%s%s", l.workspace(), fixLabel(l.label()));
    }

    private <S> List<S> filterPackages(Buffer<S> symbols, Function<S, Enumeration.Value> symbolTypeAccessor) {
        List<S> symbolsList = JavaConverters.bufferAsJavaList(symbols.toBuffer());
        return symbolsList.stream()
                .filter(x -> symbolTypeAccessor.apply(x) != SymbolType.PACKAGE())
                .collect(Collectors.toList());
    }

    private <S> Map<String, Set<String>> toLabelsMap(List<S> symbols, Function<S, String> fqnAccessor,
                                                     Function<S, Set<String>> labelsAccessor) {
        return symbols.stream()
                .collect(Collectors.toMap(fqnAccessor, labelsAccessor, (x, y) -> {
                    Set<String> res = new HashSet<>(x);
                    res.addAll(y);
                    return res;
                }));
    }

    private String fixLabel(String label) {
        String[] labelParts = label.split(":", 2);

        String labelPackage = labelParts[0].replaceAll("([^/]+)(\\.)([^/]+)", "$1/$3");
        String labelName = labelParts.length == 2 ? labelParts[1] : "";

        return labelParts.length == 2 ?
                String.format("%s:%s", labelPackage, labelName) :
                labelPackage;
    }
}

