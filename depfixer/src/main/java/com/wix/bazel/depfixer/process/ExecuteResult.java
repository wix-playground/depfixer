package com.wix.bazel.depfixer.process;

import java.util.List;

public class ExecuteResult {
    public String command;
    public int exitCode;
    public String stdout;
    public String stderr;
    public List<String> stdoutLines;
    public List<String> stderrLines;
}
