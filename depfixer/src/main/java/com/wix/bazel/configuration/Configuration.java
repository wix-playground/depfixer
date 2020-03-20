package com.wix.bazel.configuration;


import java.nio.file.Paths;
import java.util.*;

public class Configuration {
    private ConfigurationSource[] sources;

    public Configuration(ConfigurationSource... sources) {
        int length = sources.length;
        this.sources = Arrays.copyOf(sources, length + 1);
        this.sources[length] = new DefaultConfiguration();
    }

    private Optional<String> find(String key) {
        for (ConfigurationSource source : sources) {
            Optional<String> value = source.find(key);
            if (value.isPresent()) {
                System.out.println("Found value for " + key + " " + value.get() + " in source " + source.getClass().getSimpleName());

                return value;
            }
        }

        return Optional.empty();
    }

    public String getRepoPath() {
        return find("repo").orElse(null);
    }

    public String getTargets() {
        return find("targets").orElse(null);
    }

    public String getOutputDir() {
        return find("outputDir").orElse(null);
    }

    public int getRunLimit() {
        return find("runLimit").map(Integer::valueOf).get();
    }

    public String getIndexDir() {
        return find("indexDir").orElse(null);
    }

    public List<String> getBazelOpts() {
        return Arrays.stream(sources)
                .findFirst()
                .map(ConfigurationSource::unrecognizedOptions)
                .orElse(Collections.emptyList());
    }

    public boolean isCleanMode() {
        return find("clean_mode").map(Boolean::valueOf).orElse(false);
    }

    public boolean isBepMode() {
        return find("bep_mode").map(Boolean::valueOf).orElse(false);
    }

    public interface ConfigurationSource {
        Optional<String> find(String key);

        default List<String> unrecognizedOptions(){
            return Collections.emptyList();
        };
    }

    private static class DefaultConfiguration implements ConfigurationSource {
        private static Map<String, String> configuration = new HashMap<>();

        static {
            configuration.put("runLimit", String.valueOf(Integer.MAX_VALUE));
            configuration.put(
                    "indexDir",
                    Paths.get(System.getProperty("user.home"), ".depfixer-index").toAbsolutePath().toString()
            );
            configuration.put("clean_mode", "false");
            configuration.put("bep_mode", "false");
        }

        @Override
        public Optional<String> find(String key) {
            return Optional.ofNullable(configuration.get(key));
        }
    }


}

