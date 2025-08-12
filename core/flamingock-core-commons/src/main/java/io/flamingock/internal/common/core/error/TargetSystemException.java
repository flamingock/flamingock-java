/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.error;

import java.util.Set;

/**
 * Exception thrown when target system configuration or resolution fails.
 * 
 * <p>This exception provides comprehensive context about target system issues including:
 * <ul>
 *   <li>The target system ID that was requested</li>
 *   <li>Available target systems in the configuration</li>
 *   <li>Validation mode (strict vs relaxed)</li>
 *   <li>Fallback behavior and resolution status</li>
 *   <li>Configuration suggestions and resolution guidance</li>
 * </ul>
 * 
 * <p>This exception should be used for target system validation failures, missing
 * target systems, configuration errors, and initialization problems.
 * 
 * @since 6.0.0
 */
public class TargetSystemException extends FlamingockException {

    public enum FailureType {
        NOT_FOUND,
        INVALID_CONFIGURATION,
        INITIALIZATION_FAILED,
        VALIDATION_FAILED,
        DEPENDENCY_MISSING
    }

    private final FailureType failureType;
    private final String requestedTargetSystemId;
    private final Set<String> availableTargetSystems;
    private final boolean strictValidation;
    private final String fallbackTargetSystemId;
    private final String resolutionSuggestion;

    /**
     * Creates a new TargetSystemException with comprehensive context.
     *
     * @param message descriptive error message
     * @param failureType the specific type of target system failure
     * @param requestedTargetSystemId the target system ID that was requested
     * @param availableTargetSystems set of available/configured target system IDs
     * @param strictValidation whether strict validation was enabled
     * @param fallbackTargetSystemId the fallback target system ID (if applicable)
     * @param resolutionSuggestion guidance on how to resolve the issue
     * @param cause the underlying exception that caused the failure
     */
    public TargetSystemException(String message,
                                FailureType failureType,
                                String requestedTargetSystemId,
                                Set<String> availableTargetSystems,
                                boolean strictValidation,
                                String fallbackTargetSystemId,
                                String resolutionSuggestion,
                                Throwable cause) {
        super(buildTargetSystemMessage(message, failureType, requestedTargetSystemId, 
                                     availableTargetSystems, strictValidation, 
                                     fallbackTargetSystemId, resolutionSuggestion), cause);
        this.failureType = failureType;
        this.requestedTargetSystemId = requestedTargetSystemId;
        this.availableTargetSystems = availableTargetSystems;
        this.strictValidation = strictValidation;
        this.fallbackTargetSystemId = fallbackTargetSystemId;
        this.resolutionSuggestion = resolutionSuggestion;
    }

    /**
     * Creates a TargetSystemException with minimal context (for backward compatibility).
     *
     * @param message descriptive error message
     * @param failureType the type of target system failure
     * @param cause the underlying exception
     */
    public TargetSystemException(String message, FailureType failureType, Throwable cause) {
        this(message, failureType, null, null, false, null, null, cause);
    }

    /**
     * Factory method for target system not found errors.
     *
     * @param requestedId the target system ID that wasn't found
     * @param availableIds the available target system IDs
     * @param strictValidation whether strict validation is enabled
     */
    public static TargetSystemException notFound(String requestedId, Set<String> availableIds, boolean strictValidation) {
        String message = String.format("Target system '%s' not found", requestedId);
        String suggestion;
        
        if (availableIds == null || availableIds.isEmpty()) {
            suggestion = "Configure at least one target system in your Flamingock builder";
        } else if (availableIds.size() == 1) {
            suggestion = String.format("Use target system '%s' or configure '%s'", 
                                     availableIds.iterator().next(), requestedId);
        } else {
            suggestion = String.format("Use one of: [%s] or configure '%s'", 
                                     String.join(", ", availableIds), requestedId);
        }
        
        return new TargetSystemException(message, FailureType.NOT_FOUND, requestedId, 
                                       availableIds, strictValidation, null, suggestion, null);
    }

    /**
     * Factory method for target system initialization failures.
     *
     * @param targetSystemId the target system that failed to initialize
     * @param cause the underlying initialization error
     */
    public static TargetSystemException initializationFailed(String targetSystemId, Throwable cause) {
        String message = String.format("Failed to initialize target system '%s'", targetSystemId);
        String suggestion = "Check target system configuration and dependencies";
        
        return new TargetSystemException(message, FailureType.INITIALIZATION_FAILED, targetSystemId,
                                       null, false, null, suggestion, cause);
    }

    private static String buildTargetSystemMessage(String message,
                                                  FailureType failureType,
                                                  String requestedTargetSystemId,
                                                  Set<String> availableTargetSystems,
                                                  boolean strictValidation,
                                                  String fallbackTargetSystemId,
                                                  String resolutionSuggestion) {
        StringBuilder contextMessage = new StringBuilder(message);
        
        if (failureType != null) {
            contextMessage.append("\n  Failure Type: ").append(failureType);
        }
        if (requestedTargetSystemId != null) {
            contextMessage.append("\n  Requested: ").append(requestedTargetSystemId);
        }
        if (availableTargetSystems != null && !availableTargetSystems.isEmpty()) {
            contextMessage.append("\n  Available: [").append(String.join(", ", availableTargetSystems)).append("]");
        }
        contextMessage.append("\n  Validation Mode: ").append(strictValidation ? "strict" : "relaxed");
        if (fallbackTargetSystemId != null) {
            contextMessage.append("\n  Fallback: ").append(fallbackTargetSystemId);
        }
        if (resolutionSuggestion != null) {
            contextMessage.append("\n  Resolution: ").append(resolutionSuggestion);
        }
        
        return contextMessage.toString();
    }

    // Getters for programmatic access
    public FailureType getFailureType() { return failureType; }
    public String getRequestedTargetSystemId() { return requestedTargetSystemId; }
    public Set<String> getAvailableTargetSystems() { return availableTargetSystems; }
    public boolean isStrictValidation() { return strictValidation; }
    public String getFallbackTargetSystemId() { return fallbackTargetSystemId; }
    public String getResolutionSuggestion() { return resolutionSuggestion; }
}