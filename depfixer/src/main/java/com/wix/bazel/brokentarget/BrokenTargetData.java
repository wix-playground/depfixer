package com.wix.bazel.brokentarget;

import java.util.Objects;

public class BrokenTargetData {
    protected String targetName;
    protected int start;
    protected int end;

    protected String fullStream;
    protected String type;
    protected int errorStart;
    protected boolean external;
    protected String repoName;

    protected boolean testOnly;
    protected boolean fromBep;

    public String getName() {
        return targetName;
    }

    public int getStart() {
        return start;
    }

    public String getStream() {
        String s = fromBep ? fullStream : fullStream.substring(start, end);
        int idx = type.equals("proto") ?
                s.indexOf("java.lang.RuntimeException: Failed proto generation") :
                s.indexOf("java.lang.RuntimeException: Build failed");

        if (idx > -1) {
            return s.substring(0, idx);
        }

        idx = s.indexOf("INFO: From ");

        if (idx > -1) {
            return s.substring(0, idx);
        }

        return s;
    }

    public String getType() {
        return type;
    }

    public boolean isExternal() {
        return external;
    }

    public String getRepoName() {
        return repoName;
    }

    public boolean isTestOnly() {
        return testOnly;
    }

    public void setTestOnly(boolean testOnly) {
        this.testOnly = testOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetName, start, end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BrokenTargetData that = (BrokenTargetData) o;
        return start == that.start &&
                end == that.end &&
                Objects.equals(targetName, that.targetName);
    }

    @Override
    public String toString() {
        return "BrokenTargetData{" +
                "targetName='" + targetName + '\'' +
                ", errorStart='" + errorStart + '\'' +
                ", start=" + start +
                ", end=" + end +
                ", type=" + type +
                '}';
    }
}
