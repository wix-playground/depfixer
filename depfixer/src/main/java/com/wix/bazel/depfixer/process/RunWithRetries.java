package com.wix.bazel.depfixer.process;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class RunWithRetries {

    public static <T> T run(int maxRetires, Callable<T> action) {
        return run(maxRetires, 0L, action);
    }

    public static <T> T run(int maxRetires, long waitInMs, Callable<T> action) {
        int retry = 0;

        while (retry < maxRetires) {
            try {
                return action.call();
            } catch (Exception e) {
                if (++retry >= maxRetires) {
                    throw new RuntimeException(String.format("Failed after %d attempts", maxRetires), e);
                } else if (waitInMs > 0) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(waitInMs);
                    } catch (InterruptedException e1) {
                        //there is nothing we can do here
                    }
                }
            }
        }

        return null;
    }
}
