package com.wix.bazel.depfixer.configuration;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class ConfigurationTest {

    static Map<String, String> values1 = new HashMap<>();
    static Map<String, String> values2 = new HashMap<>();

    static {
        values1.put("repo", "source1-repo");
        values1.put("targets", "//source1_target");
        values1.put("outputDir", "source1-output-dir");
        values1.put("runLimit", "1");
        values1.put("indexDir", "source1-index-dir");
        values1.put("bazelOpts", "will-be-ignored");
        values1.put("clean_mode", "true");
        values1.put("bep_mode", "true");
        values1.put("labeldex_url", "source1-labeldex-url");

        values2.put("repo", "source2-repo");
        values2.put("targets", "//source2_target");
        values2.put("outputDir", "source2-output-dir");
        values2.put("runLimit", "2");
        values2.put("indexDir", "source2-index-dir");
        values2.put("bazelOpts", "will-be-ignored");
        values2.put("clean_mode", "false");
    }

    Configuration.ConfigurationSource source1 = key -> Optional.ofNullable(values1.get(key));
    Configuration.ConfigurationSource source2 = key -> Optional.ofNullable(values2.get(key));

    @Test
    public void loadsDefaultValuesForNoSources() {
        Configuration configuration = new Configuration();

        assertNull(configuration.getRepoPath());
        assertNull(configuration.getTargets());
        assertNull(configuration.getOutputDir());
        assertEquals(Integer.MAX_VALUE, configuration.getRunLimit());
        assertNotNull(configuration.getIndexDir());
        assertFalse(configuration.isCleanMode());
        assertFalse(configuration.isBepMode());
        assertNull(configuration.getLabeldexUrl());

        assertTrue(configuration.getBazelOpts().isEmpty());
    }

    @Test
    public void loadsDefaultValuesForAnEmptySource() {
        Configuration.ConfigurationSource emptySource = key -> Optional.empty();

        Configuration configuration = new Configuration(emptySource);

        assertNull(configuration.getRepoPath());
        assertNull(configuration.getTargets());
        assertNull(configuration.getOutputDir());
        assertEquals(Integer.MAX_VALUE, configuration.getRunLimit());
        assertNotNull(configuration.getIndexDir());
        assertFalse(configuration.isCleanMode());
        assertFalse(configuration.isBepMode());
        assertNull(configuration.getLabeldexUrl());

        assertTrue(configuration.getBazelOpts().isEmpty());
    }

    @Test
    public void loadsAllValuesFromSource() {
        Configuration configuration = new Configuration(source1);

        assertEquals("source1-repo", configuration.getRepoPath());
        assertEquals("//source1_target", configuration.getTargets());
        assertEquals("source1-output-dir", configuration.getOutputDir());
        assertEquals(1, configuration.getRunLimit());
        assertNotNull(configuration.getIndexDir());
        assertTrue(configuration.isCleanMode());
        assertTrue(configuration.isBepMode());
        assertEquals("source1-labeldex-url", configuration.getLabeldexUrl());

        assertTrue(configuration.getBazelOpts().isEmpty());
    }

    @Test
    public void returnsValuesInOrderFirstFound() {
        Configuration configuration = new Configuration(source2, source1);

        // values from source2
        assertEquals("source2-repo", configuration.getRepoPath());
        assertEquals("//source2_target", configuration.getTargets());
        assertEquals("source2-output-dir", configuration.getOutputDir());
        assertEquals(2, configuration.getRunLimit());
        assertNotNull(configuration.getIndexDir());
        assertFalse(configuration.isCleanMode());

        // values from source 1
        assertTrue(configuration.isBepMode());
        assertEquals("source1-labeldex-url", configuration.getLabeldexUrl());

        assertTrue(configuration.getBazelOpts().isEmpty());
    }

}
