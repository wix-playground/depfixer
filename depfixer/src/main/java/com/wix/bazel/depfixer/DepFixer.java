package com.wix.bazel.depfixer;

import com.wix.bazel.depfixer.analyze.*;
import com.wix.bazel.depfixer.brokentarget.BrokenTargetData;
import com.wix.bazel.depfixer.brokentarget.BrokenTargetExtractor;
import com.wix.bazel.depfixer.brokentarget.BrokenTargetExtractorFactory;
import com.wix.bazel.depfixer.cache.ExternalCache;
import com.wix.bazel.depfixer.cache.ExternalCacheFactory;
import com.wix.bazel.depfixer.cache.RepoCache;
import com.wix.bazel.depfixer.cache.TargetsStore;
import com.wix.bazel.depfixer.configuration.Configuration;
import com.wix.bazel.depfixer.impl.PrimeAppExtension;
import com.wix.bazel.depfixer.overrides.Overrides;
import com.wix.bazel.depfixer.process.ExecuteResult;
import com.wix.bazel.depfixer.process.ProcessRunner;
import com.wix.bazel.depfixer.process.RunWithRetries;
import com.wix.bazel.depfixer.repo.AbstractBazelIndexer;
import com.wix.bazel.depfixer.repo.ExternalRepoIndexer;
import com.wix.bazel.depfixer.repo.InternalRepoIndexer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.System.exit;
import static java.util.stream.Collectors.*;

public class DepFixer {
    private static final Pattern brokenFile = Pattern.compile("(.+):\\d+: error:");

    private Set<String> addDepsInstructions = null;
    private Map<String, Set<String>> repoToInstructions = null;

    private Path repoPath;
    private String targetsToBuild;

    private Path executionPath;

    private Map<String, Set<String>> targetDepsHistory = new HashMap<>();

    private AnalyzerContext analyzerContext;
    private TargetAnalyzer targetAnalyzer;
    private ExternalCache externalCache;
    private String workspaceName;
    private TargetsStore targetsStore;
    private int runLimit;
    private Configuration configuration;
    private ExternalCacheFactory externalCacheFactory;
    private BrokenTargetExtractorFactory brokenTargetExtractorFactory;

    private AbstractBazelIndexer externalBazelIndexer, internalBazelIndexer;

    private Overrides overrides;

    public DepFixer(
            Configuration configuration,
            Overrides overrides,
            ExternalCacheFactory externalCacheFactory,
            BrokenTargetExtractorFactory brokenTargetExtractorFactory) {
        this.configuration = configuration;
        this.overrides = overrides;
        this.externalCacheFactory = externalCacheFactory;
        this.brokenTargetExtractorFactory = brokenTargetExtractorFactory;
    }

    public void fix() throws IOException, InterruptedException, ExecutionException {
        repoPath = resolveRepoPath(configuration.getRepoPath());
        targetsToBuild = resolveTargetsToBuild(repoPath, configuration.getTargets());

        executionPath = configuration.getOutputDir() == null ?
                Files.createTempDirectory("deps_") :
                Paths.get(configuration.getOutputDir());

        runLimit = configuration.getRunLimit();

        analyzerContext = new AnalyzerContext();

        targetAnalyzer = new UberAnalyzer(analyzerContext);

        long time = System.currentTimeMillis();
        int code;

        try {
            code = fixBuild();
        } finally {
            System.out.println("Total time " + (System.currentTimeMillis() - time) / 1000 + " secs");
            ProcessRunner.shutdownNow();
        }

        exit(code);
    }

    private Path resolveRepoPath(String path) {
        if (path != null) {
            return Paths.get(path);
        }

        Path currentPath = getWorkingPath();

        while (currentPath != null) {
            if (Files.isRegularFile(currentPath.resolve("WORKSPACE"))) {
                break;
            }

            currentPath = currentPath.getParent();
        }

        Objects.requireNonNull(currentPath, String.format("%s is not within a workspace",
                Paths.get("").toAbsolutePath()));

        return currentPath;
    }

    private Path getWorkingPath() {
        String buildWorkspaceDirectory = System.getenv("BUILD_WORKING_DIRECTORY");

        return buildWorkspaceDirectory == null ?
                Paths.get("").toAbsolutePath() :
                Paths.get(buildWorkspaceDirectory);
    }


    private BrokenTargetExtractor initBrokenTargetExtractor(Path runPath, String stderr) {
        return brokenTargetExtractorFactory.create(repoPath, analyzerContext.getBazelExternalPath(), runPath, stderr);
    }

    private String resolveTargetsToBuild(Path repoPath, String targets) {
        if (targets != null) {
            return targets;
        }

        Path currentPath = getWorkingPath();
        Path relativePath = repoPath.relativize(currentPath);

        return String.format("//%s/...", relativePath.toString()).replace("///", "//");
    }

    private void populatePaths() throws IOException, InterruptedException, ExecutionException {
        analyzerContext.setBazelOutPath(findBazelOut());
        analyzerContext.setBazelExternalPath(findBazelExternal());
        analyzerContext.setRepoPath(repoPath);
    }

    private Path findBazelOut() throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(repoPath, buildBazelCmd("info", "output_path"));

        if (res.exitCode > 0)
            throw new RuntimeException("failed to get output_path [" + res.exitCode + "]\n" + res.stderr);

        Optional<Path> maybeInternalPath = Stream.of("darwin-fastbuild", "k8-fastbuild")
                .map(d -> String.format("/%s/bin", d))
                .map(p -> Paths.get(res.stdout, p))
                .filter(Files::isDirectory)
                .findFirst();

        return maybeInternalPath.orElseThrow(() -> new RuntimeException("Failed to find bazel out"));
    }

    private Path findBazelExternal() throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(repoPath, buildBazelCmd("info", "output_base"));

        if (res.exitCode > 0)
            throw new RuntimeException("failed to get output_base [" + res.exitCode + "]\n" + res.stderr);

        return Paths.get(res.stdout, "external");
    }

    private int fixBuild() throws IOException, InterruptedException, ExecutionException {
        analyzerContext.setRunNumber(1);

        int beforeQuitCounter = 5;

        do {
            System.out.println("Run number " + analyzerContext.getRunNumber());
            repoToInstructions = new HashMap<>();

            Instant start = Instant.now();

            Instant end = Instant.now();

            if (analyzerContext.getRunNumber() == 1) {
                workspaceName = getWorkspaceName();

                Objects.requireNonNull(workspaceName, "Failed to get workspace name");
                Objects.requireNonNull(workspaceName.isEmpty() ? null : workspaceName, "Failed to get workspace name");

                executionPath = configuration.getOutputDir() == null ?
                        Paths.get(System.getProperty("user.home"), ".depfixer", workspaceName) :
                        Paths.get(configuration.getOutputDir());

                if (Files.exists(executionPath)) {
                    Files.walk(executionPath)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }

                Files.createDirectories(executionPath);
            }

            Path runPath = executionPath.resolve(String.format("run-%03d", analyzerContext.getRunNumber()));
            Files.createDirectories(runPath);

            List<String> args = brokenTargetExtractorFactory.initArgs(runPath, targetsToBuild);
            ExecuteResult res = ProcessRunner.execute(repoPath, buildBazelCmd("build", args.toArray(new String[0])));

            Files.write(runPath.resolve("run.log"), res.stderr.getBytes());

            if (res.exitCode > 0) {
                if (res.stdout.contains("Duplicate file:")) {
                    throw new RuntimeException("Duplicate file error found! Stopping!");
                }

                if (analyzerContext.getRunNumber() == 1) {
                    populatePaths();
                    targetsStore = new TargetsStore(repoPath, configuration);
                    targetsStore.updateTestOnlyTargets(getTestOnlyTargets(repoPath));
                    targetsStore.updateTargetsToIgnore(getTargetsWithTag(repoPath, "no-tool"));

                    targetsStore.installExtension(new PrimeAppExtension(repoPath, configuration));
                    targetsStore.update();

                    if (configuration.getLabeldexUrl() != null) {
                        externalCache = externalCacheFactory.create(workspaceName, targetsStore);
                    }

                    externalBazelIndexer = new ExternalRepoIndexer(
                            repoPath,
                            Paths.get(configuration.getIndexDir()),
                            workspaceName,
                            analyzerContext.getBazelExternalPath(),
                            targetsStore
                    );

                    internalBazelIndexer = new InternalRepoIndexer(
                            repoPath,
                            Paths.get(configuration.getIndexDir()),
                            workspaceName,
                            analyzerContext.getBazelOutPath(),
                            targetsStore,
                            analyzerContext.getBazelExternalPath()
                    );
                }
                BrokenTargetExtractor targetExtractor = initBrokenTargetExtractor(runPath, res.stderr);
                RepoCache external = externalBazelIndexer.index();
                RepoCache internal = internalBazelIndexer.index();

                analyzerContext.setExternalCache(external);
                analyzerContext.setInternalCache(internal);

                List<BrokenTargetData> brokenTargets = targetExtractor.extract();

                for (BrokenTargetData brokenTarget : brokenTargets) {
                    markType(brokenTarget);
                }

                System.out.println("Total Targets: " + brokenTargets.size());

                analyze(brokenTargets);

                Map<String, List<String>> reposLogs = brokenTargets.stream()
                        .collect(groupingBy(BrokenTargetData::getRepoName,
                                mapping(BrokenTargetData::getStream, toList())));

                boolean changesWereMade = false;

                backtracking(res.stderr, repoToInstructions.computeIfAbsent("//", k -> new HashSet<>()));

                for (Map.Entry<String, Set<String>> instructions : repoToInstructions.entrySet()) {
                    String repoName = instructions.getKey().replace("//", "_main_");
                    changesWereMade = addStrictDepsInstructionsAndApplyAll(repoName, instructions, runPath, reposLogs) || changesWereMade;
                }

                long secs = Duration.between(start, end).getSeconds();
                String totalDurationStr = String.format("%02d-%02d-%02d", secs / 3600, (secs % 3600) / 60, (secs % 60));
                Files.move(runPath, executionPath.resolve(
                        String.format("run-%03d-%s", analyzerContext.getRunNumber(), totalDurationStr))
                );

                if (!changesWereMade) {
                    if (brokenTargets.isEmpty()) {
                        if (res.stderr.contains("INFO: Analysis succeeded for only")) {
                            throw new RuntimeException("Build is broken due to analysis failures");
                        } else {
                            throw new RuntimeException("Build is broken but couldn't find what is broken");
                        }
                    } else if (--beforeQuitCounter == 0) {
                        throw new RuntimeException("Failed to make any changes to fix the build - quitting");
                    }
                } else {
                    beforeQuitCounter = 5;
                }

                if (runLimit == analyzerContext.getRunNumber()) {
                    return 3;
                }
            } else {
                break;
            }

            analyzerContext.incrementRunNumber();
        } while (true);

        return 0;
    }

    private void backtracking(String stderr, Set<String> instructions) throws IOException, InterruptedException, ExecutionException {
        fixNoSuchPackage(stderr, instructions);
        fixNoSuchTarget(stderr, instructions);
        fixRuleDoesNotExists(stderr, instructions);
        fixTargetNotVisible(stderr, instructions);
        fixCycle(stderr);
        fixTestOnly(stderr);
    }

    private void fixNoSuchPackage(String stderr, Set<String> instructions) throws IOException {
        Pattern p = Pattern.compile("ERROR: ([^:]+):\\d+:\\d+: no such package '([^']+)': [^\n]+\n\\s+-[^\n]+ and referenced by '([^']+)(?:_test_runner)?'");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            String targetToRemove = cleanPackage(m.group(2));
            final String targetToRemoveFrom = clearTestRunner(m.group(3));

            if (!targetToRemove.startsWith("//"))
                targetToRemove = "//" + targetToRemove;

            final String toRemove = targetToRemove;
            if (!targetToRemove.contains(":")) {
                String buildFile = m.group(1);
                Files.lines(Paths.get(buildFile))
                        .filter(l -> l.contains('"' + toRemove + ":") || l.contains('"' + toRemove + '"'))
                        .map(l -> {
                            int idx = l.indexOf('"');
                            int nextIdx = l.indexOf('"', idx + 1);
                            return l.substring(idx + 1, nextIdx);
                        })
                        .forEach(t -> removeDep(instructions, t, targetToRemoveFrom));
            } else {
                removeDep(instructions, targetToRemove, targetToRemoveFrom);
            }

        }
    }

    private void fixNoSuchTarget(String stderr, Set<String> instructions) {
        Pattern p = Pattern.compile("no such target '([^']+)': .+ and referenced by '([^']+)(?:_test_runner)?'");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            removeDep(instructions, m.group(1), clearTestRunner(m.group(2)));
        }
    }

    private void fixRuleDoesNotExists(String stderr, Set<String> instructions) {
        Pattern p = Pattern.compile("in deps attribute of scala_library rule (.+): rule '([^']+)' does not exist");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            removeDep(instructions, m.group(2), clearTestRunner(m.group(1)));
        }
    }

    private void fixTargetNotVisible(String stderr, Set<String> instructions)
            throws IOException, InterruptedException, ExecutionException {
        Pattern p = Pattern.compile("target '([^']+)' is not visible from target '([^']+)'");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            String addedDep = m.group(1);
            String srcDep = clearTestRunner(m.group(2));

            removeDep(instructions, addedDep, srcDep);

            String targetToAdd = tryToGetExportTarget(repoPath, addedDep);

            if (targetToAdd != null) {
                instructions.add(String.format("add %s %s|%s",
                        targetsStore.getAttributeForTarget(srcDep, targetsStore.isTestOnly(srcDep)),
                        targetToAdd,
                        targetsStore.getRealTargetName(srcDep)));
            }
        }
    }

    private void fixTestOnly(String stderr) {
        Pattern p = Pattern.compile("non-test target '([^']+)' depends on testonly target '([^']+)'");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            String srcTarget = m.group(1);
            String testonlyTarget = m.group(2);

            String rootRepoName = getRepoName(srcTarget);

            removeDep(repoToInstructions.computeIfAbsent(rootRepoName, k -> new HashSet<>()), testonlyTarget, srcTarget);
        }
    }

    private void fixCycle(String stderr) {
        Pattern p = Pattern.compile("in scala_library rule (.+): cycle in dependency");
        Matcher m = p.matcher(stderr);

        while (m.find()) {
            String rootDep = m.group(1);
            p = Pattern.compile("\\.->(.+)`--", Pattern.DOTALL);
            m = p.matcher(stderr.substring(m.end()));
            if (m.find()) {
                String[] depsInCycle = m.group(1).trim().split("\\|\\s+");

                //The first dep in the cycle is the actual target (rootDep), the 2nd is the cause
                String cycleCause = depsInCycle[1].replaceAll("\\s", "");

                String rootRepoName = getRepoName(rootDep);
                String causeRepoName = getRepoName(cycleCause);

                if (causeRepoName.equals(rootRepoName)) {
                    cycleCause = "//" + cycleCause.split("//", 2)[1];
                }

                removeDep(repoToInstructions.computeIfAbsent(rootRepoName, k -> new HashSet<>()), cycleCause, rootDep);
            }
        }
    }

    private String getRepoName(String rootDep) {
        String repoName;
        if (rootDep.startsWith("@")) {
            repoName = rootDep.split("//")[0].substring(1);
        } else {
            repoName = "//";
        }
        return repoName;
    }

    private void removeDep(Set<String> instructions, String targetToRemove, String targetToRemoveFrom) {
        instructions.add(String.format("remove %s %s|%s",
                targetsStore.getAttributeForTarget(targetToRemoveFrom, targetsStore.isTestOnly(targetToRemoveFrom)),
                targetToRemove,
                targetsStore.getRealTargetName(targetToRemoveFrom)));
    }

    private String clearTestRunner(String target) {
        return target.replace("_test_runner", "");
    }

    private String cleanPackage(String pck) {
        if (pck.endsWith("//")) {
            pck = pck.substring(0, pck.length() - 2);
        }

        return pck;
    }

    private void markType(BrokenTargetData targetData) {
        if (targetData.isExternal()) {
            return;
        }

        targetData.setTestOnly(targetsStore.isTestOnly(targetData.getName()));
    }

    private Set<String> getTestOnlyTargets(Path path) throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(path,
                buildBazelCmd("query", "attr(testonly, 1, //...)"));

        if (res.exitCode > 0) {
            throw new RuntimeException("Failed to get test targets info: [" + res.stderr + "]");
        }

        return Stream.of(res.stdout.split("\n")).collect(toSet());
    }

    private Set<String> getTargetsWithTag(Path path, String tag)
            throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(path,
                buildBazelCmd("query", String.format("attr(tags, '%s', //...)", tag)));

        if (res.exitCode > 0) {
            throw new RuntimeException("Failed to get targets by tag info: [" + res.stderr + "]");
        }

        return Stream.of(res.stdout.split("\n")).collect(toSet());
    }

    private String tryToGetExportTarget(Path path, String target)
            throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(path,
                buildBazelCmd("query", String.format("attr(exports, %s, //...)", target)));

        if (res.exitCode > 0) {
            throw new RuntimeException("Failed to get test targets info: [" + res.stderr + "]");
        }

        String[] exports = res.stdout.split("\n");

        return exports.length == 0 ? null : exports[0];
    }

    private String[] buildBazelCmd(String cmd, String... args) {
        List<String> cmdArgs = new LinkedList<>();
        cmdArgs.add("bazel");
        cmdArgs.addAll(configuration.getBazelOpts());
        cmdArgs.add(cmd);
        cmdArgs.addAll(Arrays.asList(args));

        return cmdArgs.toArray(new String[0]);
    }

    private boolean addStrictDepsInstructionsAndApplyAll(String repoName,
                                                         Map.Entry<String, Set<String>> repoNameWithItsInstructions,
                                                         Path runPath,
                                                         Map<String, List<String>> reposLogs)
            throws IOException, InterruptedException, ExecutionException {
        Path instructionsFile = runPath.resolve(String.format("repo_%s_deps.txt", repoName));
        Path repoLogFile = runPath.resolve(String.format("repo_%s.log", repoName));

        Files.write(instructionsFile, repoNameWithItsInstructions.getValue());
        Files.write(repoLogFile, reposLogs.computeIfAbsent(repoNameWithItsInstructions.getKey(), k -> Collections.emptyList()));

        Path workspacePath = repoName.equals("_main_") ? repoPath : calculateExternalRepoPath(repoName);

        return applyInstructions(instructionsFile, workspacePath);
    }

    private Path calculateExternalRepoPath(String repoName) {
        Path externalPath = analyzerContext.getBazelExternalPath();
        return externalPath.resolve(repoName);
    }

    private boolean applyInstructions(Path instructionsScript, Path repoPath)
            throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(repoPath, "buildozer", "-f", instructionsScript.toString());

        if (res.exitCode != 0 && res.exitCode != 3) {
            throw new RuntimeException("Failed to apply: [" + instructionsScript + "]\n" + res.stderr);
        }

        return res.exitCode == 0;
    }

    private void analyze(List<BrokenTargetData> brokenTargets) throws IOException {

        for (BrokenTargetData brokenTarget : brokenTargets) {
            Set<String> history = targetDepsHistory.computeIfAbsent(brokenTarget.getName(), k -> new HashSet<>());

            analyzerContext.setTargetDepsHistory(history);
            analyzeTarget(brokenTarget);

            Set<String> repoInstructions = repoToInstructions.computeIfAbsent(brokenTarget.getRepoName(), k -> new HashSet<>());

            repoInstructions.addAll(addDepsInstructions);
        }
    }

    private void analyzeTarget(BrokenTargetData targetData) throws IOException {
        addDepsInstructions = new HashSet<>();

        String targetStream = targetData.getStream();

        Matcher brokenFileMatcher = brokenFile.matcher(targetStream);
        Map<String, String> fileContentMap = new HashMap<>();
        while (brokenFileMatcher.find()) {
            String file = brokenFileMatcher.group(1);

            if ("<macro>".equals(file)) {
                continue;
            }

            Path filePath = file.startsWith("external/") ?
                    analyzerContext.getBazelExternalPath().resolve(file.split("/", 2)[1]) :
                    repoPath.resolve(file);

            if (!Files.isRegularFile(filePath)) {
                throw new RuntimeException(String.format("File [%s] is broken and it is not current repo file", filePath.toString()));
            }

            String content = new String(Files.readAllBytes(filePath));
            fileContentMap.put(file, content);
        }

        analyzerContext.setFileToContentMap(fileContentMap);

        AnalyzerResult result = targetAnalyzer.analyze(targetData);

        AnalyzerResult aggResult = new AnalyzerResult();
        aggResult.merge(result);

        Set<String> targets = new HashSet<>();
        while (aggResult.isNotEmpty()) {
            Set<String> classes = aggResult.removeHighestScore();
            TargetLocatorResponse response = getTargets(classes, targetData, false);
            targets.addAll(response.targets);

            Set<String> classesWithoutTargets = new HashSet<>(response.classesWithoutTargets);

            if (response.targets.isEmpty()) {
                Set<String> otherClasses = new HashSet<>();
                for (String cls : classesWithoutTargets) {
                    if (cls.indexOf('.') > -1) {
                        otherClasses.add(cls.substring(0, cls.lastIndexOf('.')));
                    }
                }

                response = getTargets(otherClasses, targetData, true);
                targets.addAll(response.targets);
            }

            //There are no new targets from current hints - try to look for packages
            if (response.targets.isEmpty()) {
                Set<String> packages = new HashSet<>();
                for (String cls : classesWithoutTargets) {
                    if (isPackage(cls)) {
                        packages.add(cls);
                    } else if (ImportAnalysis.isFqcn(cls)) {
                        String pack = toPackage(cls);
                        if (pack != null) {
                            packages.add(pack + ".package");
                        }
                    } else {
                        if (cls.indexOf('.') > -1) {
                            String pack = cls.substring(0, cls.lastIndexOf('.'));
                            packages.add(pack + ".package");
                        }
                    }
                }

                TargetLocatorResponse packageResponse = getTargets(packages, targetData, false);
                targets.addAll(packageResponse.targets);

                if (packageResponse.targets.isEmpty()) {
                    packages = new HashSet<>();
                    for (String cls : classesWithoutTargets) {
                        if (isPackage(cls) || ImportAnalysis.isFqcn(cls)) {
                            continue;
                        }

                        packages.add(cls + ".package");
                    }

                    packageResponse = getTargets(packages, targetData, true);
                    targets.addAll(packageResponse.targets);
                }
            }

            if (!targets.isEmpty()) {
                addAndCleanTargetDeps(targetData, targets);
                break;
            }

            //couldn't find targets for current score - move on to next score
        }
    }

    private TargetLocatorResponse getTargets(Set<String> classes,
                                             BrokenTargetData targetData, boolean forceGlobal) {
        Set<String> targets = new HashSet<>();
        Set<String> classesWithoutTarget = new HashSet<>();

        for (String cls : classes) {
            cls = overrides.overrideClass(cls);
            Set<String> targetsForClass = getTargetsForClass(cls, targetData);

            targets.addAll(targetsForClass);

            if (targetsForClass.isEmpty()) {
                classesWithoutTarget.add(cls);
            }
        }

        targets.removeAll(analyzerContext.getTargetDepsHistory());

        //go to labeldex with all classes without local targets
        List<String> fqcns = classesWithoutTarget.stream()
                .filter(x -> forceGlobal || ImportAnalysis.isFqcn(x) || isPackage(x))
                .collect(toList());

        if (externalCache != null) {
            RepoCache cache = externalCache.get(fqcns);

            for (String cls : fqcns) {
                String target = cache.get(cls, targetData, analyzerContext.getTargetDepsHistory());

                if (target != null) {
                    targets.add(target);
                    classesWithoutTarget.remove(cls);
                }
            }
        }

        targets.removeAll(analyzerContext.getTargetDepsHistory());

        return new TargetLocatorResponse(targets, classesWithoutTarget);
    }

    private String toPackage(String fqcn) {
        String[] parts = fqcn.split("\\.");
        List<String> agg = new ArrayList<>();

        for (String part : parts) {
            if (Character.isLowerCase(part.charAt(0))) {
                agg.add(part);
            } else {
                break;
            }
        }

        if (!agg.isEmpty()) {
            return String.join(".", agg);
        }

        return null;
    }

    private Set<String> getTargetsForClass(String cls, BrokenTargetData targetData) {
        Set<String> targets = new HashSet<>();
        String target = overrides.overrideClassToTarget(cls);

        if (target == null)
            target = analyzerContext.getInternalCache().get(cls, targetData, analyzerContext.getTargetDepsHistory());

        if (target == null) {
            target = analyzerContext.getExternalCache().get(cls, targetData, analyzerContext.getTargetDepsHistory());

            if (target != null && !target.startsWith("@")) {
                target = "@" + target;
            }
        }

        if (target != null && target.startsWith("@org_scala_lang_scala_"))
            target = null;

        if (target != null) {
            targets.add(target);

            if (target.equals("@org_specs2_specs2_core_2_12")) {
                targets.add("@org_specs2_specs2_common_2_12");
            } else if (target.equals("@org_specs2_specs2_common_2_12")) {
                targets.add("@org_specs2_specs2_core_2_12");
            } else if (target.contains("@com_typesafe_akka")) {
                targets.add("@com_typesafe_akka_akka_actor_2_12");
            }

        } else if (cls.equals("scala.xml")) {
            targets.add("@org_scala_lang_modules_scala_xml_2_12");
        }

        return targets;
    }

    private boolean isPackage(String s) {
        return s.endsWith(".package");
    }

    private void addAndCleanTargetDeps(BrokenTargetData data, Set<String> targetDeps) {
        Set<String> targetDepsHistory = analyzerContext.getTargetDepsHistory();

        targetDeps.removeAll(targetDepsHistory);
        targetDepsHistory.addAll(targetDeps);

        targetDeps.stream().filter(t -> !t.startsWith("@org_scala_lang_scala_library")).forEach(t -> {
            String targetName = data.isExternal() ?
                    ("//" + data.getName().split("//", 2)[1]) :
                    data.getName();
            String target = t.replace("@//", "@" + workspaceName + "//");

            addDepsInstructions.add(String.format("add %s %s|%s",
                    targetsStore.getAttributeForTarget(targetName, targetsStore.isTestOnly(targetName)),
                    target,
                    targetsStore.getRealTargetName(targetName)));
        });

    }

    private String tryToGetWorkspaceName() throws IOException, InterruptedException, ExecutionException {
        ExecuteResult res = ProcessRunner.execute(repoPath, buildBazelCmd("info", "execution_root"));

        if (res.exitCode > 0) {
            throw new RuntimeException("Failed to execute `bazel info execution_root` [" + res.stderr + "]");
        }

        String executionRoot = res.stdout;
        res = ProcessRunner.execute(repoPath, "basename", executionRoot);

        if (res.exitCode > 0) {
            throw new RuntimeException(String.format("Failed to execute `basename %s` [%s]", executionRoot, res.stderr));
        }

        String workspace = res.stdout;

        if (workspace == null || workspace.isEmpty()) {
            throw new RuntimeException("Something is really weird here - workspace name is either empty or null");
        }

        return res.stdout;
    }

    private String getWorkspaceName() {
        return RunWithRetries.run(10, this::tryToGetWorkspaceName);
    }

    private static class TargetLocatorResponse {
        Set<String> targets;
        Set<String> classesWithoutTargets;

        TargetLocatorResponse(Set<String> targets, Set<String> classesWithoutTargets) {
            this.targets = targets;
            this.classesWithoutTargets = classesWithoutTargets;
        }
    }
}
