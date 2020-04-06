package com.wix.bazel.impl;

import com.wix.bazel.configuration.Configuration;
import com.wix.bazel.process.ExecuteResult;
import com.wix.bazel.process.ProcessRunner;
import com.wix.bazel.repo.TargetsStoreExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

public class PrimeAppExtension extends TargetsStoreExtension {
    private Map<String, String> targetsToPrimeAppMap = new HashMap<>();

    public PrimeAppExtension(Path repoPath, Configuration configuration) {
        super(repoPath, configuration);
    }

    public void update() {
        targetsToPrimeAppMap = getPrimeAppTargets();
    }

    @Override
    public String getRealTargetName(String target) {
        return targetsToPrimeAppMap.getOrDefault(target, target);
    }

    @Override
    public String getAttributeForTarget(boolean testOnlyTarget) {
        return testOnlyTarget? "deps_test" : "deps";
    }

    private Map<String, String> getPrimeAppTargets() {
        ExecuteResult res;
        try {
            res = ProcessRunner.execute(repoPath,
                    query("attr(tags, 'prime-app-generated-*', //...)", "--output", "xml"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get prime-app targets", e);
        }

        if (res.exitCode > 0) {
            throw new RuntimeException("Failed to get prime-app targets: [" + res.stderr + "]");
        }

        try {
            return parseQueryResults(res.stdout);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse xml results", e);
        }
    }

    private String[] query(String... args) {
        List<String> cmdArgs = new LinkedList<>();
        cmdArgs.add("bazel");
        cmdArgs.addAll(configuration.getBazelOpts());
        cmdArgs.add("query");
        cmdArgs.addAll(Arrays.asList(args));

        return cmdArgs.toArray(new String[0]);
    }

    private Map<String, String> parseQueryResults(String res) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(res)));

        NodeList rules = doc.getElementsByTagName("rule");

        Map<String, String> targetsToPrimeApp = new HashMap<>();

        for (int i = 0; i < rules.getLength(); i++) {
            Element rule = (Element) rules.item(i);
            String ruleName = rule.getAttribute("name");

            NodeList lists = rule.getElementsByTagName("list");
            Element tags = null;

            for (int j = 0; j < lists.getLength(); j++) {
                Element listElement = (Element) lists.item(j);
                if ("tags".equals(listElement.getAttribute("name"))) {
                   tags = listElement;
                   break;
                }
            }

            if (tags != null) {
                NodeList strings = tags.getElementsByTagName("string");

                for (int j = 0; j < strings.getLength(); j++) {
                    Element string = (Element) strings.item(j);
                    String value = string.getAttribute("value");
                    if (value != null && value.startsWith("prime-app-generated-")) {
                        String macroName = value.substring("prime-app-generated-".length());
                        String fullMacroName = ruleName.split(":")[0] + ":" + macroName;

                        targetsToPrimeApp.put(ruleName, fullMacroName);
                    }
                }
            }
        }

        return targetsToPrimeApp;
    }
}
