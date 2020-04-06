package com.wix.bazel.depfixer.configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public class UserConfigSource implements Configuration.ConfigurationSource {
    private static final Properties properties = new Properties();

    static {
        Path config = Paths.get(System.getProperty("user.home"), ".depfixer/config").toAbsolutePath();
        if (config.toFile().exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(config.toFile());
                properties.load(inputStream);
            } catch (IOException e) {
                System.out.println("Failed reading config file .depfixer/config");
            }
        }
    }

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }
}
