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
package io.flamingock.core.processor.util;

/**
 * Utility class for resolving and processing location paths in the Flamingock annotation processor.
 * <p>
 * Handles path-related operations such as:
 * <ul>
 *     <li>Distinguishing between package names and file paths</li>
 *     <li>Deriving names from location strings</li>
 *     <li>Processing resource location strings</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class PathResolver {

    private PathResolver() {
        // Utility class - prevent instantiation
    }

    /**
     * Determines if the given location string represents a Java package name.
     * <p>
     * A package name contains dots and no slashes (e.g., {@code "com.example.migrations"}).
     * <p>
     * Examples:
     * <pre>
     * isPackageName("com.example.migrations") → true
     * isPackageName("resources/db/migrations") → false
     * isPackageName("/absolute/path") → false
     * </pre>
     *
     * @param location the location string to check
     * @return {@code true} if the location is a package name, {@code false} otherwise
     */
    public static boolean isPackageName(String location) {
        return location.contains(".") && !location.contains("/");
    }

    /**
     * Derives a stage name from the location string by extracting the last segment.
     * <p>
     * This method handles both package names and file paths:
     * <ul>
     *     <li>Package names are split by dots</li>
     *     <li>File paths are split by slashes</li>
     *     <li>Strips {@code "resources/"} prefix if present</li>
     * </ul>
     * <p>
     * Examples:
     * <pre>
     * deriveNameFromLocation("com.example.migrations") → "migrations"
     * deriveNameFromLocation("resources/db/migrations") → "migrations"
     * deriveNameFromLocation("/absolute/path/to/migrations") → "migrations"
     * </pre>
     *
     * @param location the location string
     * @return the derived name (last segment of the location)
     */
    public static String deriveNameFromLocation(String location) {
        // Remove "resources/" prefix if present
        String cleanLocation = location;
        if (cleanLocation.startsWith("resources/")) {
            cleanLocation = cleanLocation.substring("resources/".length());
        }

        // Split by either dots (for packages) or slashes (for paths)
        String[] segments;
        if (cleanLocation.contains(".") && !cleanLocation.contains("/")) {
            segments = cleanLocation.split("\\.");
        } else {
            segments = cleanLocation.split("/");
        }

        // Get the last non-empty segment
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i].trim();
            if (!segment.isEmpty()) {
                return segment;
            }
        }

        // Fallback to original location if no segments found
        return location;
    }

    /**
     * Processes a resource location to handle the {@code "resources/"} prefix.
     * <p>
     * Strips the {@code "resources/"} prefix if present to avoid double-prefixing when
     * the location is later concatenated with {@code resourcesRoot} (e.g., {@code "src/main/resources"}).
     * <p>
     * Examples:
     * <pre>
     * processResourceLocation("resources/flamingock/pipeline") → "flamingock/pipeline"
     * processResourceLocation("flamingock/pipeline") → "flamingock/pipeline"
     * processResourceLocation(null) → null
     * </pre>
     *
     * @param location the location string from user input
     * @return processed location with {@code "resources/"} prefix stripped if present
     */
    public static String processResourceLocation(String location) {
        return location != null && location.startsWith("resources/")
                ? location.substring("resources/".length())
                : location;
    }
}
