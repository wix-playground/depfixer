package com.wix.bazel.repo;

import com.wix.bazel.process.RunWithRetries;
import com.wix.bazel.runmode.RunMode;
import com.wixpress.ci.labeldex.domain.SymbolType;
import com.wixpress.ci.labeldex.model.BulkLabels;
import com.wixpress.ci.labeldex.model.BulkSymbols;
import com.wixpress.ci.labeldex.model.Label;
import com.wixpress.ci.labeldex.model.SymbolLabels;
import com.wixpress.ci.labeldex.model.BazelLabels;
import com.wixpress.ci.labeldex.model.SecondPartyLabel;
import com.wixpress.ci.labeldex.model.ThirdPartyLabel;
import com.wixpress.ci.labeldex.restclient.LabeldexRestClient;
import com.wixpress.ci.labeldex.domain.SymbolType.*;
import com.wixpress.ci.rest.client.RestClient;
import scala.Option;
import scala.collection.JavaConverters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GlobalExternalCache {
    private String srcWorkspaceName;
    private LabeldexRestClient labeldexrestClient = new LabeldexRestClient(new RestClient("http://bo.wix.com/labeldex-webapp", 3000));
    private RepoCache cache;
    private RunMode runMode;

    public GlobalExternalCache(Set<String> tetOnlyTargets, String srcWorkspaceName, RunMode runMode) {
        this.cache = new RepoCache(tetOnlyTargets);
        this.srcWorkspaceName = srcWorkspaceName;
        this.runMode = runMode;
    }

    public RepoCache get(List<String> classes) {
        List<String> filteredClasses = classes.stream()
                .filter(x -> !cache.getClasses().contains(x))
                .collect(Collectors.toList());

        if (filteredClasses.isEmpty()) {
            return cache;
        }

        if (runMode == RunMode.ISOLATED) {
            getSingleLabelsAndAddToCache(filteredClasses);
        } else {
            getBulkLabelsAndAddToCache(filteredClasses);
        }

        return cache;
    }

    private void getBulkLabelsAndAddToCache(List<String> classes) {
        BulkSymbols symbols = new BulkSymbols(JavaConverters.asScalaBuffer(classes).toSet());
        BulkLabels clientLabels =
                RunWithRetries.run(5, 500L, () -> labeldexrestClient.findBulkLabels(symbols));
        List<SymbolLabels> symbolLabels = JavaConverters.bufferAsJavaList(clientLabels.symbolDocument().toBuffer());

        symbolLabels.stream().filter(l -> l.symbol().symbolType() != SymbolType.PACKAGE()).forEach(s -> {
            Supplier<Stream<Label>> labelsStreamSupplier = () -> JavaConverters.setAsJavaSet(s.labels()).stream()
                    .filter(l -> !l.workspace().equals(srcWorkspaceName));

            Supplier<Stream<Label>> fwLabelsStreamSupplier = () ->
                    labelsStreamSupplier.get().filter(l -> l.workspace().equals("wix_platform_wix_framework"));

            Stream<Label> labelsStream;

            if (fwLabelsStreamSupplier.get().findAny().isPresent()) {
                labelsStream = fwLabelsStreamSupplier.get();
            } else {
                labelsStream = labelsStreamSupplier.get();
            }

            labelsStream.forEach(l -> cache.put(s.symbol().fullyQualifiedName(),
                    String.format("@%s%s", l.workspace(), fixLabel(l.label()))));
        });
    }

    private String fixLabel(String label) {
        String[] labelParts = label.split(":", 2);

        String labelPackage = labelParts[0].replaceAll("([^/]+)(\\.)([^/]+)", "$1/$3");
        String labelName = labelParts.length == 2 ? labelParts[1] : "";

        return labelParts.length == 2 ?
                String.format("%s:%s", labelPackage, labelName) :
                labelPackage;
    }

    private void getSingleLabelsAndAddToCache(List<String> classes) {
        classes.forEach(className -> {
            BazelLabels labels = RunWithRetries.run(
                    5,
                    500L,
                    () -> labeldexrestClient.findLabels(className, Option.apply(srcWorkspaceName), false));
            Set<String> labelsToAdd = new HashSet<>();
            if (labels.thirdPartyLabels().nonEmpty()) {
                labelsToAdd = getThirdPartyLabels(labels.thirdPartyLabels());
            } else if (labels.secondPartyLabels().nonEmpty()) {
                labelsToAdd = getSecondPartyLabels(labels.secondPartyLabels());
            }
            labelsToAdd.forEach(label -> cache.put(className, label));
        });
    }

    private Set<String> getSecondPartyLabels(scala.collection.immutable.Set<SecondPartyLabel> secondPartyLabels) {
        return JavaConverters.setAsJavaSet(secondPartyLabels).stream()
                .map(SecondPartyLabel::label)
                .collect(Collectors.toSet());
    }

    private Set<String> getThirdPartyLabels(scala.collection.immutable.Set<ThirdPartyLabel> thirdPartyLabels) {
        return JavaConverters.setAsJavaSet(thirdPartyLabels).stream()
                .map(ThirdPartyLabel::label)
                .collect(Collectors.toSet());
    }
}

