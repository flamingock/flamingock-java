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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoadedChangeUtil {



    // For template files: must start with _order_ (e.g., _002_whatever.yaml)
    private static final String SIMPLE_FILE_ORDER_REGEX = "^_([^_]+)_";

    // For class names: must have _order_ at the beginning of the class name after package
    // (e.g., com.mycompany.mypackage._002_mychange or com.mycompany.OuterClass$_002_innerchange)
    private static final String FILE_WITH_PACKAGE_ORDER_REGEX = "[.$]_([^_]+)_";

    private LoadedChangeUtil() {
    }

    /**
     * For TemplateLoadedChange - validates order from template file name
     */
    public static String getMatchedOrderFromFile(String changeId, String orderInContent, String fileName) {
        return getMatchedOrder(changeId, orderInContent, fileName, false);
    }

    /**
     * For CodeLoadedChange - validates order from class name
     */
    public static String getMatchedOrderFromClassName(String changeId, String orderInAnnotation, String className) {
        return getMatchedOrder(changeId, orderInAnnotation, className, true);
    }

    /**
     * Common validation logic for both template files and class names
     */
    private static String getMatchedOrder(String changeId,
                                          String orderInContent,
                                          String fileName,
                                          boolean isCodeBased) {
        boolean hasOrderInContent = orderInContent != null && !orderInContent.equals("NULL_VALUE");
        String orderFromFileName = getOrderFromFileName(fileName, isCodeBased);

        if (hasOrderInContent) {
            if (orderFromFileName != null) {
                if (orderInContent.equals(orderFromFileName)) {
                    return orderInContent;
                } else {
                    throw mismatchOrderException(changeId, orderInContent, isCodeBased, orderFromFileName);
                }
            } else {
                return orderInContent;
            }
        } else {
            if (orderFromFileName != null) {
                return orderFromFileName;
            } else {
                throw missingOrderException(changeId, isCodeBased);
            }
        }
    }

    private static FlamingockException mismatchOrderException(String changeId, String orderInContent, boolean isCodeBased, String orderInFileName) {

        String orderInContentText;
        String fileType;
        if(isCodeBased) {
            orderInContentText = String.format("@Change(order='%s')", orderInContent);
            fileType = "className";
        } else {
            orderInContentText = String.format("value in template order field='%s'", orderInContent);
            fileType = "fileName";
        }

        return new FlamingockException(String.format("Change[%s] Order mismatch: %s does not match order in %s='%s'",
                changeId, orderInContentText, fileType, orderInFileName));
    }

    private static FlamingockException missingOrderException(String changeId, boolean isCodeBased) {

        String contentType;
        String fileType;
        String fileExt;
        if(isCodeBased) {
            contentType = "@Change annotation";
            fileType = "className";
            fileExt = "java";
        } else {
            contentType = "template order field";
            fileType = "fileName";
            fileExt = "yaml";
        }

        return new FlamingockException(String.format("Change[%s] Order is required: order must be present in the %s or in the %s(e.g. _0001_%s.%s). If present in both, they must have the same value.",
                changeId, contentType, fileType, changeId, fileExt));
    }

    private static String getOrderFromFileName(String fileName, boolean withPackage) {
        if (fileName == null) {
            return null;
        }
        String patternToUse = withPackage ? FILE_WITH_PACKAGE_ORDER_REGEX : SIMPLE_FILE_ORDER_REGEX;

        Pattern pattern = Pattern.compile(patternToUse);
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

}
