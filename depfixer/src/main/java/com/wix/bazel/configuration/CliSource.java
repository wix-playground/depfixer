package com.wix.bazel.configuration;

import org.apache.commons.cli.*;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class CliSource implements Configuration.ConfigurationSource {
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
    private static Option labeldexUrl = createOption("labeldexUrl",
            "URL for Labeldex service",
            "labeldex_url");

    private List<String> bazelOpts;
    private CommandLine commandLine;

    private CliSource() {
    }

    private CliSource(
            List<String> bazelOpts,
            CommandLine commandLine) {
        this.bazelOpts = bazelOpts;
        this.commandLine = commandLine;
    }

    private static CliSource empty() {
        return new CliSource();
    }

    public static CliSource parseCliParameters(String[] args) {

        Options cliOptions = new Options();
        cliOptions.addOption(cliRepo);
        cliOptions.addOption(cliTargets);
        cliOptions.addOption(cliOutputDir);
        cliOptions.addOption(cliRunLimit);
        cliOptions.addOption(cliIndexDir);
        cliOptions.addOption(cleanModeOpt);
        cliOptions.addOption(bepModeOpt);
        cliOptions.addOption(labeldexUrl);

        try {

            Parser parser = new Parser();
            CommandLine cliParameters = parser.parse(cliOptions, args);

            return new CliSource(parser.bazelArgs, cliParameters);

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
        Option.Builder builder = Option.builder().desc(description);
        if (hasArg) {
            builder.argName(argName);
            builder.hasArg();
        }
        builder.longOpt(optionName);

        return builder.build();
    }

    @Override
    public Optional<String> find(String key) {
        if (commandLine.hasOption(key)) {
            String value = Optional.ofNullable(commandLine.getOptionValue(key)).orElse("true");
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
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
