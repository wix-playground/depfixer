package com.wix.bazel.analyze;

import com.wix.bazel.brokentarget.BrokenTargetData;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wix.bazel.analyze.ImportAnalysis.fqcn;
import static com.wix.bazel.analyze.ImportAnalysis.packageImport;

public class TremNotFoundAnalyzer extends AbstractTargetAnalyzer {
    private static Pattern pattern =
            Pattern.compile("error: Symbol 'term ([^']+)' is missing from the classpath\\.\n" +
                    "This symbol is required by 'value ([^']+)'.");

    protected TremNotFoundAnalyzer(AnalyzerContext ctx) {
        super(ctx);
    }

    @Override
    public AnalyzerResult analyze(BrokenTargetData targetData) {
        AnalyzerResult res = new AnalyzerResult();

        String stream = targetData.getStream();
        Matcher matcher = pattern.matcher(stream);

        while (matcher.find()) {
            String missingTerm = matcher.group(1);
            String requiredBy = matcher.group(2);

            Optional<String> maybeRequiredByFqn = fqcn(requiredBy);

            if (!maybeRequiredByFqn.isPresent()) {
                maybeRequiredByFqn = packageImport(requiredBy);
                if (!maybeRequiredByFqn.isPresent()) {
                    continue;
                }
            }

            String requiredByFqn = maybeRequiredByFqn.get();
            String requiredByTarget = ctx.getInternalCache().get(requiredByFqn, targetData);

            if (requiredByTarget == null) {
                continue;
            }

            String requiredByTargetPackage = requiredByTarget.split(":", 2)[0];
            Path root = ctx.getRepoPath();
            if (requiredByTarget.startsWith("@")) {
                String[] packageParts = requiredByTargetPackage.split("//", 2);

                String repo = packageParts[0].substring(1);
                root = ctx.getBazelExternalPath().resolve(repo);

                requiredByTargetPackage = packageParts[1];
            }

            Path requiredByTargetRelativePath = Paths.get(requiredByTargetPackage.replace("//", ""));
            Path requiredByTargetPath = root.resolve(requiredByTargetRelativePath);

            if (!Files.isDirectory(requiredByTargetPath)) {
                continue;
            }

            String requiredByFileName =
                    Paths.get(requiredByFqn.replace(".", "/")).getFileName().toString();

            ByNameVisitor byNameVisitor = new ByNameVisitor(requiredByFileName);
            try {
                Files.walkFileTree(requiredByTargetPath, byNameVisitor);
                if (byNameVisitor.foundFile != null) {
                    String cls = extractClass(byNameVisitor.foundFile, missingTerm);
                    if (cls != null) {
                        res.addClass(cls, 50);
                    }
                    continue;
                }
            } catch (IOException e) {
                continue;
            }

            ByContentVisitor byContentVisitor = new ByContentVisitor(requiredByFileName);
            try {
                Files.walkFileTree(requiredByTargetPath, byContentVisitor);
                if (byContentVisitor.foundFile != null) {
                    String cls = extractClass(byContentVisitor.foundFile, missingTerm);

                    if (cls != null) {
                        res.addClass(cls, 50);
                    }
                }
            } catch (IOException e) {
            }
        }

        return res;
    }

    private String extractClass(Path file, String missingTerm) throws IOException {
        String content = new String(Files.readAllBytes(file));

        Set<String> imports = ImportAnalysis.extractImports(content);

        Optional<String> maybeClass = imports.stream()
                .filter(x -> x.startsWith(missingTerm))
                .filter(ImportAnalysis::isFqcn)
                .findFirst();

        return maybeClass.orElse(null);
    }

    private static class ByNameVisitor extends SimpleFileVisitor<Path> {
        String fileToFind;
        Path foundFile;

        ByNameVisitor(String fileToFind) {
            this.fileToFind = fileToFind;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile() && file.getFileName().toString().startsWith(fileToFind + ".")) {
                foundFile = file;

                return FileVisitResult.TERMINATE;
            }

            return FileVisitResult.CONTINUE;
        }
    }

    private static class ByContentVisitor extends ByNameVisitor {

        ByContentVisitor(String fileToFind) {
            super(fileToFind);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (attrs.isRegularFile()) {
                String content = new String(Files.readAllBytes(file));

                if (content.contains("class " + fileToFind) || content.contains("object " + fileToFind)) {
                    foundFile = file;
                    return FileVisitResult.TERMINATE;
                }

            }

            return FileVisitResult.CONTINUE;
        }
    }
}
