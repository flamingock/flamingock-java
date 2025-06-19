/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.internal.util;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FileUtil {


    private FileUtil() {
    }


    public static List<File> getAllYamlFiles(File directory) {
        FilenameFilter fileNameFilter = (dir, name) -> name.endsWith(".yaml");
        return getAllFiles(directory, fileNameFilter);
    }

    public static List<File> getAllFiles(File directory, FilenameFilter filenameFilter) {
        File[] files = directory.listFiles(filenameFilter);
        return Arrays.asList(Objects.requireNonNull(files));
    }


    public static File getFile(String parentDir, String childDir, boolean check) {
        File file = new File(parentDir, childDir);
        if(check && !file.exists()) {
            throw new RuntimeException("File not found: "  + file.getAbsolutePath());
        }
        return file;
    }

    public static List<File> loadFilesFromDirectory(String directory, ClassLoader classLoader) {
        try {
            URL resource = classLoader.getResource(directory);
            if (resource == null) {
                throw new RuntimeException("Resource not found: "  + directory);
            }
            return Arrays.asList(Objects.requireNonNull(new File(resource.toURI()).listFiles()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T getFromYamlFile(File file, Class<T> type) {
        try {
            return new Yaml(new Constructor(type, new LoaderOptions())).load(Files.newInputStream(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T getFromMap(Class<T> type, Object source) {
        Yaml yamlWriter = new Yaml();
        StringWriter writer = new StringWriter();
        yamlWriter.dump(source, writer);
        String string = writer.toString();
        Constructor constructor = new Constructor(type, new LoaderOptions());
        Yaml yaml = new Yaml(constructor);
        return yaml
                .load(string);
    }

    public static boolean isExistingDir(File directory) {
        return directory.exists() && directory.isDirectory();
    }
}
