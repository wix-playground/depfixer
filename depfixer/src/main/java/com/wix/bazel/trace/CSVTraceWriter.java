package com.wix.bazel.trace;

import java.io.*;
import java.nio.file.Path;
import java.util.List;

public class CSVTraceWriter {
    public void write(List<TraceItem> data, Path tracePath) {
        try {
            File out = tracePath.toFile();
            FileOutputStream fos = new FileOutputStream(out);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

            bw.write("run,analyzer,target,class,labeldex,added dependency");
            bw.newLine();
            for (TraceItem item : data) {
                bw.write(toCSVRow(item));
                bw.newLine();
            }
            bw.close();
        } catch (Exception ex) {
            throw new RuntimeException("Error while writing trace file", ex);
        }
    }

    private String toCSVRow(TraceItem item) {
        return String.format(
                "%d,%s,%s,%s,%s,%s",
                item.runNumber,
                item.analyzerClassName,
                item.targetData.getName(),
                item.className,
                item.labeldex? "x" : "",
                item.resolvedTarget);
    }
}
