package com.wix.bazel.depfixer.configuration;

import org.junit.Test;

import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class CliSourceTest {
    @Test
    public void parsesCommandLineArgs() {
        String[] args = {
                "-repo", "some-repo",
                "-targets", "some-targets",
                "-outputDir", "some-output-dir",
                "-runLimit", "1",
                "-indexDir", "some-index-dir",
                "-clean_mode",
                "-bep_mode",
                "-labeldex_url", "some-labeldex-url",
                "--one=val1",
                "--two=val2",
                "-t=val3",
        };

        CliSource source = CliSource.parseCliParameters(args);

        assertEquals(Optional.of("some-repo"), source.find("repo"));
        assertEquals(Optional.of("some-targets"), source.find("targets"));
        assertEquals(Optional.of("some-output-dir"), source.find("outputDir"));
        assertEquals(Optional.of("1"), source.find("runLimit"));
        assertEquals(Optional.of("some-index-dir"), source.find("indexDir"));
        assertEquals(Optional.of("true"), source.find("clean_mode"));
        assertEquals(Optional.of("true"), source.find("bep_mode"));
        assertEquals(Optional.of("some-labeldex-url"), source.find("labeldex_url"));
        assertEquals(asList("--one=val1", "--two=val2", "-t=val3"), source.unrecognizedOptions());
    }
}
