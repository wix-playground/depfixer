package com.wix.bazel.depfixer.configuration;

import com.wix.bazel.configuration.ClasspathSource;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ClasspathConfigurationSourceTest {
    @Test
    public void loadsValuesFromClasspath() {
        ClasspathSource source = new ClasspathSource();

        assertEquals(Optional.of("http://labeldex.example.com"), source.find("labeldex_url"));
    }
}
