package com.wix.bazel.depfixer.repo;

import com.wix.bazel.depfixer.cache.RepoCache;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JarFileProcessor {

    public static void addClassesToCache(Path file, String target, RepoCache cache) throws IOException {
        ZipInputStream is = new ZipInputStream(new FileInputStream(file.toFile()));
        ZipEntry ze;

        while ((ze = is.getNextEntry()) != null) {
            String name = ze.getName();

            if (name.endsWith(".class")) {
                String fqn = name
                        .replace("$.class", "")
                        .replace(".class", "")
                        .replace("`", "")
                        .replace('/', '.')
                        .replaceAll("\\$([^$]+)?", ".$1");
                cache.put(file.toString(), fqn, target);
            }
        }
    }

}
