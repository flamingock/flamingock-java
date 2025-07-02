package io.flamingock.internal.core.task.loaded;

import io.flamingock.internal.common.core.error.FlamingockException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoadedChangeUnitUtil {

    // For template files: must start with _order_ (e.g., _002_whatever.yaml)
    private static final String SIMPLE_FILE_ORDER_REGEX = "^_([^_]+)_";
    
    // For class names: must have _order_ at the beginning of the class name after package 
    // (e.g., com.mycompany.mypackage._002_mychange)
    private static final String FILE_WITH_PACKAGE_ORDER_REGEX = "\\._([^_]+)_[^.]*$";

    private LoadedChangeUnitUtil() {
    }

    /**
     * For TemplateLoadedChangeUnit - validates order from template file name
     */
    public static String getMatchedOrderFromFile(String changeUnitId, String orderInContent, String fileName) {
        return getMatchedOrder(changeUnitId, orderInContent, fileName, false);
    }

    /**
     * For CodeLoadedChangeUnit - validates order from class name
     */
    public static String getMatchedOrderFromClassName(String changeUnitId, String orderInContent, String className) {
        return getMatchedOrder(changeUnitId, orderInContent, className, true);
    }

    /**
     * Common validation logic for both template files and class names
     */
    private static String getMatchedOrder(String changeUnitId,
                                          String orderInContent,
                                          String fileName,
                                          boolean isCodeBased) {
        boolean hasOrderInContent = orderInContent != null && !orderInContent.trim().isEmpty();
        String orderFromFileName = getOrderFromFileName(fileName, isCodeBased);

        if (hasOrderInContent) {
            if (orderFromFileName != null) {
                if (orderInContent.equals(orderFromFileName)) {
                    return orderInContent;
                } else {
                    throw mismatchOrderException(changeUnitId, orderInContent, isCodeBased, orderFromFileName);
                }
            } else {
                return orderInContent;
            }
        } else {
            if (orderFromFileName != null) {
                return orderFromFileName;
            } else {
                throw missingOrderException(changeUnitId, isCodeBased);
            }
        }
    }

    private static FlamingockException mismatchOrderException(String changeUnitId, String orderInContent, boolean isCodeBased, String orderInFileName) {

        String orderInContentText;
        String fileType;
        if(isCodeBased) {
            orderInContentText = String.format("@ChangeUnit(order='%s')", orderInContent);
            fileType = "className";
        } else {
            orderInContentText = String.format("value in template order field='%s'", orderInContent);
            fileType = "fileName";
        }

        return new FlamingockException(String.format("ChangeUnit[%s] Order mismatch: %s does not match order in %s='%s'",
                changeUnitId, orderInContentText, fileType, orderInFileName));
    }

    private static FlamingockException missingOrderException(String changeUnitId, boolean isCodeBased) {

        String contentType;
        String fileType;
        String fileExt;
        if(isCodeBased) {
            contentType = "@ChangeUnit annotation";
            fileType = "className";
            fileExt = "java";
        } else {
            contentType = "template order field";
            fileType = "fileName";
            fileExt = "yaml";
        }

        return new FlamingockException(String.format("ChangeUnit[%s] Order is required: order must be present in the %s or in the %s(e.g. _0001_%s.%s). If present in both, they must have the same value.",
                changeUnitId, contentType, fileType, changeUnitId, fileExt));
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
