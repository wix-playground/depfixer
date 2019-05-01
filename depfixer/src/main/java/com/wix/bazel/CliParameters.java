package com.wix.bazel;

import com.wix.bazel.runmode.RunMode;
import org.apache.commons.cli.*;

import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

public class CliParameters {

    private String repoPath;
    private String targets;
    private String outputDir;
    private int runLimit;
    private RunMode runMode;
    private String indexDir;
    private List<String> bazelOpts;
    private boolean cleanMode;

    private CliParameters() {}

    private CliParameters(String repoPath,
                          String targets,
                          String outputDir,
                          int runLimit,
                          RunMode runMode,
                          String indexDir,
                          List<String> bazelOpts,
                          boolean cleanMode) {
        this.repoPath = repoPath;
        this.targets = targets;
        this.outputDir = outputDir;
        this.runLimit = runLimit;
        this.runMode = runMode;
        this.indexDir = indexDir;
        this.bazelOpts = bazelOpts;
        this.cleanMode = cleanMode;
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
        cliOptions.addOption(cliRunMode);
        cliOptions.addOption(cliIndexDir);
        cliOptions.addOption(cleanModeOpt);

        try {

            Parser parser = new Parser();
            CommandLine cliParameters = parser.parse(cliOptions, args);

            String defaultIndexDir = Paths.get(System.getProperty("user.home"), ".depfixer-index").toAbsolutePath().toString();

            String repoPath = getOptionValue(cliParameters, "repo");
            String targets = getOptionValue(cliParameters, "targets");
            String outputDir = getOptionValue(cliParameters, "outputDir");
            int runLimit = getOptionValue(cliParameters, "runLimit",
                    Integer::parseInt, Integer.MAX_VALUE);
            RunMode runMode = getOptionValue(cliParameters, "mode",
                    RunMode::valueOf, RunMode.ISOLATED);
            String indexDir = getOptionValue(cliParameters,"indexDir", Function.identity(), defaultIndexDir);

            return new CliParameters(repoPath, targets, outputDir, runLimit, runMode, indexDir, parser.bazelArgs, cliParameters.hasOption("clean_mode"));

        } catch (Exception ex) {
            printHelp(cliOptions);
            return empty();
        }

    }

    private static String getOptionValue(CommandLine cliParameters, String optionName) {
        return getOptionValue(cliParameters, optionName, Function.identity());
    }

    private static <T> T getOptionValue(CommandLine cliParameters, String optionName, Function<String, T> func) {
        return getOptionValue(cliParameters, optionName, func, null);
    }

    private static <T> T getOptionValue(CommandLine cliParameters, String optionName,
                                        Function<String, T> func, T defaultValue) {
        return cliParameters.hasOption(optionName) ?
                func.apply(cliParameters.getOptionValue(optionName)) :
                defaultValue;
    }

    private static void printHelp(Options cliOptions) {
        new HelpFormatter().printHelp("Usage example", cliOptions);
        System.exit(1);
    }

    public String getRepoPath() {
        return repoPath;
    }
    public String getTargets() {
        return targets;
    }
    public String getOutputDir() {
        return outputDir;
    }

    public int getRunLimit() {
        return runLimit;
    }

    public RunMode getRunMode() {
        return runMode;
    }

    public String getIndexDir() {
        return indexDir;
    }

    public List<String> getBazelOpts() {
        return bazelOpts;
    }

    public boolean isCleanMode() {
        return cleanMode;
    }

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

    private static Option cliRunMode = createOption("runMode",
            "Valid values are, `SOCIAL` `PARTIAL` `ISOLATED`, default is `ISOLATED`",
            "mode");

    private static Option cliIndexDir = createOption("indexDir",
            "Path to save different indexes",
            "indexDir");

    private static Option cleanModeOpt = createOption("cleanMode",
            "Use depfixer to fix repo after all deps were cleared",
            "clean_mode", false);

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
