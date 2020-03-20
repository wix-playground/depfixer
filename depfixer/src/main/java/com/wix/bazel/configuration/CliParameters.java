package com.wix.bazel.configuration;

import org.apache.commons.cli.*;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class CliParameters implements Configuration.ConfigurationSource {
    private static Option cliRepo = createOption("repoPath",
            "Path to repo - default is current repo",
            "repo");
    private static Option cliTargets = createOption("targets",
            "Targets to build - default is current package targets",
            "targets");
    private static Option cliOutputDir = createOption("outputDir",
            "Output Directory, if not specified, outputs will be written to ~/.depfixer/<repo name>",
            "outputDir");
    private static Option cliRunLimit = createOption("runLimit",
            "Maximum times to run, if not specified, default is MAXINT",
            "runLimit");
    private static Option cliIndexDir = createOption("indexDir",
            "Path to save different indexes",
            "indexDir");
    private static Option cleanModeOpt = createOption("cleanMode",
            "Use depfixer to fix repo after all deps were cleared",
            "clean_mode", false);
    private static Option bepModeOpt = createOption("bepMode",
            "Use depfixer to fix repo using Build Event Protocol output. This is usually used when running Depfixer with RBE as using stdout/stderr stream is not reliable (missing errors in stderr/nondeterministic order)",
            "bep_mode", false);
    private List<String> bazelOpts;
    private CommandLine commandLine;

    private CliParameters() {
    }

    private CliParameters(
            List<String> bazelOpts,
            CommandLine commandLine) {
        this.bazelOpts = bazelOpts;
        this.commandLine = commandLine;
    }

    private static CliParameters empty() {
        return new CliParameters();
    }

    public static CliParameters parseCliParameters(String[] args) {

        Options cliOptions = new Options();
        cliOptions.addOption(cliRepo);
        cliOptions.addOption(cliTargets);
        cliOptions.addOption(cliOutputDir);
        cliOptions.addOption(cliRunLimit);
        cliOptions.addOption(cliIndexDir);
        cliOptions.addOption(cleanModeOpt);
        cliOptions.addOption(bepModeOpt);

        try {

            Parser parser = new Parser();
            CommandLine cliParameters = parser.parse(cliOptions, args);

            return new CliParameters(parser.bazelArgs, cliParameters);

        } catch (Exception ex) {
            printHelp(cliOptions);
            return empty();
        }

    }

    private static void printHelp(Options cliOptions) {
        new HelpFormatter().printHelp("Usage example", cliOptions);
        System.exit(1);
    }

    private static Option createOption(String argName, String description, String optionName) {
        return createOption(argName, description, optionName, true);
    }

    private static Option createOption(String argName, String description, String optionName, boolean hasArg) {
        OptionBuilder.withDescription(description);

        if (hasArg) {
            OptionBuilder.withArgName(argName);
            OptionBuilder.hasArg();
        }

        return OptionBuilder.create(optionName);
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(this.commandLine.getOptionValue(key));
    }

    @Override
    public List<String> unrecognizedOptions() {
        return bazelOpts;
    }

    private static class Parser extends BasicParser {
        private List<String> bazelArgs = new LinkedList<>();

        protected void processOption(String arg, ListIterator iter) throws ParseException {
            try {
                super.processOption(arg, iter);
            } catch (UnrecognizedOptionException e) {
                bazelArgs.add(arg);
            }
        }
    }
}
