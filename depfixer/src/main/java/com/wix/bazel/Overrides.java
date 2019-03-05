package com.wix.bazel;

import java.util.HashMap;
import java.util.Map;

public class Overrides {
    private static Map<String, String> classesOverrides = new HashMap<>();
    private static Map<String, String> classesToTargetsOverrides = new HashMap<>();

    static {
        classesOverrides.put("com.wixpress.metasite.reloose.testkit", "com.wixpress.metasite.reloose.testkit.RelooseTestkit");

        classesToTargetsOverrides.put("com.wixpress.common.objectsearch.testkit.ObjectSearchTestKit",
                "@server_infra//dev-infra-contribs/object-search/object-search-testkit/src/main/scala/com/wixpress/common/objectsearch/testkit");

    }

    public static String overrideClass(String classname) {
        return classesOverrides.getOrDefault(classname, classname);
    }

    public static String overrideClassToTarget(String targetName) {
        return classesToTargetsOverrides.get(targetName);
    }
}
