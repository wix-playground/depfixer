# TL;DR  
`DepFixer` is a dependencies fixer tool.

# Background

`DepFixer` is a dependencies fixer tool and was created to solve some of the dependency adding problems we have. It does so by making sure that only the dependencies that are really needed for the compiler are added.

Currently when adding a new dependency there is usually a need to add more dependencies to the current target and sometimes even to other targets. The reasons for this are:

1. Collecting transitive compile time deps - `strict_java_deps = error`
2. `rules_scala` compiler plugin is very eager and produces many false-positives - these false-positives can cause potential issue 

# Usage
```
bazel run //depfixer/src/main/java/com/wix/bazel/depfixer/runner:depfixer_cli -- -repo <path-to-bazel-project> -targets //...
```

`DepFixer` will resolve the repository path and the targets automatically - all the targets under the current directory (`package`) will be built and fixed by `DepFixer`.

