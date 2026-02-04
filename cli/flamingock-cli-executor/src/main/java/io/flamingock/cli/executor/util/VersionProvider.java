/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cli.executor.util;

import picocli.CommandLine.IVersionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Provides version information from the JAR manifest or build properties.
 */
public class VersionProvider implements IVersionProvider {

    private static final String UNKNOWN_VERSION = "unknown";

    @Override
    public String[] getVersion() {
        String version = getVersionFromManifest();
        if (UNKNOWN_VERSION.equals(version)) {
            version = getVersionFromProperties();
        }
        return new String[]{"Flamingock CLI v" + version};
    }

    /**
     * Gets the version string for use in headers and messages.
     *
     * @return the version string
     */
    public static String getVersionString() {
        VersionProvider provider = new VersionProvider();
        String version = provider.getVersionFromManifest();
        if (UNKNOWN_VERSION.equals(version)) {
            version = provider.getVersionFromProperties();
        }
        return version;
    }

    private String getVersionFromManifest() {
        try {
            InputStream manifestStream = getClass().getClassLoader()
                    .getResourceAsStream("META-INF/MANIFEST.MF");
            if (manifestStream != null) {
                Manifest manifest = new Manifest(manifestStream);
                Attributes attrs = manifest.getMainAttributes();
                String version = attrs.getValue("Implementation-Version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (IOException e) {
            // Fall through to return unknown
        }
        return UNKNOWN_VERSION;
    }

    private String getVersionFromProperties() {
        try {
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("flamingock-cli-executor.properties");
            if (is != null) {
                props.load(is);
                String version = props.getProperty("version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (IOException e) {
            // Fall through to return unknown
        }
        return UNKNOWN_VERSION;
    }
}
