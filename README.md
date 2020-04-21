###### Disclaimer: this is a proof of concept (POC) code, use at your own risk.

# TL;DR  
`DepFixer` is a dependencies fixing tool.

## Background

`DepFixer` is a dependencies fixer tool and was created to solve some of the dependency adding problems we have. It does
 so by making sure that only the dependencies that are really needed for the compiler are added.

Currently when adding a new dependency there is usually a need to add more dependencies to the current target and
 sometimes even to other targets. The reasons for this are:

1. Collecting transitive compile time deps - `strict_java_deps = error`
2. `rules_scala` compiler plugin is very eager and produces many false-positives - these false-positives can cause
 potential issue 

## Usage
```
bazel run //depfixer/src/main/java/com/wix/bazel/depfixer/runner:depfixer_cli -- -repo <path-to-bazel-project> -targets //...
```

`DepFixer` will resolve the repository path and the targets automatically - all the targets under the current directory
 (`package`) will be built and fixed by `DepFixer`.

## DepFixer steps

### Setup
Extract and set different configuration variables from Bazel: external path, build path, test only targets, etc.

### Build
Builds targets

### Backtracking
DepFixer tries to avoid breaking the build more than it already is. This step knows how to fix the common issues that
 might be introduced during the Fix step. This only works on the main repos and doesn't work on the external repos
 
### Extract
This step analyzes the build log and extracts the broken target and their log portion

### Index
If there are broken targets this step will go over 2 directories, the external directory to index 3rd party external
 targets (3rd party and external source repos) and will index each jar file according to a set of rules. The discovery
 is done using Git
  
### Analyze
This step goes over each target and extracts a set of FQCNs (fully qualified class names). For each FQCN there is a
 score that describes the level of confidence for it. The score is set by the different analyzers.
 
### Fix
Picks the highest FQCNs that were extracted from the previous step and tries to locate labels for them from the
 different sources in the next order: internal (main repo build output), externals and global index implementation 
 (`ExternalCache`) if one is provided.


