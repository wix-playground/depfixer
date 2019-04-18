package com.wix.bazel;

import com.wix.bazel.runmode.RunMode;
import org.apache.commons.cli.*;

import java.nio.file.Paths;
import java.util.function.Function;

public class CliParameters {

    private String repoPath;
    private String targets;
    private String outputDir;
    private int runLimit;
    private RunMode runMode;
    private String indexDir;

    private CliParameters() {}

    private CliParameters(String repoPath,
                          String targets,
                          String outputDir,
                          int runLimit,
                          RunMode runMode,
                          String indexDir) {
        this.repoPath = repoPath;
        this.targets = targets;
        this.outputDir = outputDir;
        this.runLimit = runLimit;
        this.runMode = runMode;
        this.indexDir = indexDir;
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

        try {

            CommandLineParser parser = new BasicParser();
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

            return new CliParameters(repoPath, targets, outputDir, runLimit, runMode, indexDir);

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

    private static Option createOption(String argName, String description, String optionName) {
        OptionBuilder.withArgName(argName);
        OptionBuilder.withDescription(description);
        OptionBuilder.hasArg();

        return OptionBuilder.create(optionName);

    }
}
