package com.wix.bazel.repo;

public class FullyQualifiedLabel {

  public FullyQualifiedLabel(String label, String workspace) {
    this.label = label;
    this.workspace = workspace;
  }

  public String label;
  public String workspace;
}
