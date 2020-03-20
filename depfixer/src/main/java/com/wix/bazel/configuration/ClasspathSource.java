package com.wix.bazel.configuration;

import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

public class ClasspathSource implements Configuration.ConfigurationSource {
    private static final Properties properties = new Properties();

    static {
        try {
            properties.load(ClasspathSource.class.getClassLoader().getResourceAsStream("depfixer.properties"));
        } catch (IOException e) {
            System.out.println("depfixer.properties not found on from classpath");
        }
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }
}
