package com.wix.bazel;

import org.apache.commons.cli.*;

import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

public class CliParameters  {

    private String repoPath;
    private String targets;
    private String outputDir;
    private int runLimit;
    private String indexDir;
    private List<String> bazelOpts;
    private boolean cleanMode;
    private boolean bepMode;

    private CliParameters() {
    }

    private CliParameters(String repoPath,
                          String targets,
                          String outputDir,
                          int runLimit,
                          String indexDir,
                          List<String> bazelOpts,
                          boolean cleanMode,
                          boolean bepMode) {
        this.repoPath = repoPath;
        this.targets = targets;
        this.outputDir = outputDir;
        this.runLimit = runLimit;
        this.indexDir = indexDir;
        this.bazelOpts = bazelOpts;
        this.cleanMode = cleanMode;
        this.bepMode = bepMode;
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

            String defaultIndexDir = Paths.get(System.getProperty("user.home"), ".depfixer-index").toAbsolutePath().toString();

            String repoPath = getOptionValue(cliParameters, "repo");
            String targets = getOptionValue(cliParameters, "targets");
            String outputDir = getOptionValue(cliParameters, "outputDir");
            int runLimit = getOptionValue(cliParameters, "runLimit",
                    Integer::parseInt, Integer.MAX_VALUE);
            String indexDir = getOptionValue(cliParameters, "indexDir", Function.identity(), defaultIndexDir);

            return new CliParameters(repoPath, targets, outputDir, runLimit, indexDir, parser.bazelArgs, cliParameters.hasOption("clean_mode"), cliParameters.hasOption("bep_mode"));

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

    public String getIndexDir() {
        return indexDir;
    }

    public List<String> getBazelOpts() {
        return bazelOpts;
    }

    public boolean isCleanMode() {
        return cleanMode;
    }

    public boolean isBepMode() {
        return bepMode;
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

    private static Option cliIndexDir = createOption("indexDir",
            "Path to save different indexes",
            "indexDir");

    private static Option cleanModeOpt = createOption("cleanMode",
            "Use depfixer to fix repo after all deps were cleared",
            "clean_mode", false);

    private static Option bepModeOpt = createOption("bepMode",
            "Use depfixer to fix repo using Build Event Protocol output. This is usually used when running Depfixer with RBE as using stdout/stderr stream is not reliable (missing errors in stderr/nondeterministic order)",
            "bep_mode", false);

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
