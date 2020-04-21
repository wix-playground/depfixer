package com.wix.bazel;

import com.wix.bazel.depfixer.overrides.Overrides;

import java.util.HashMap;
import java.util.Map;

public class WixOverrides implements Overrides {
    private Map<String, String> classesOverrides = new HashMap<>();
    private Map<String, String> classesToTargetsOverrides = new HashMap<>();

    public WixOverrides() {
        classesOverrides.put(
                "com.wixpress.metasite.reloose.testkit", "com.wixpress.metasite.reloose.testkit.RelooseTestkit"
        );

        classesToTargetsOverrides.put(
                "com.wixpress.common.objectsearch.testkit.ObjectSearchTestKit",
                "@server_infra//dev-infra-contribs/object-search/object-search-testkit/src/main/scala/com/wixpress/common/objectsearch/testkit"
        );
    }

    public WixOverrides(Map<String, String> classesOverrides, Map<String, String> classesToTargetsOverrides) {

        this.classesOverrides = classesOverrides;
        this.classesToTargetsOverrides = classesToTargetsOverrides;
    }

    public String overrideClass(String classname) {
        return classesOverrides.getOrDefault(classname, classname);
    }

    public String overrideClassToTarget(String targetName) {
        return classesToTargetsOverrides.get(targetName);
    }
}
