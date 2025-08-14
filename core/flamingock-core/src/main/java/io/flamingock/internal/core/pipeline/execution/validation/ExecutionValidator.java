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
package io.flamingock.internal.core.pipeline.execution.validation;

import io.flamingock.internal.core.engine.audit.recovery.ManualInterventionRequiredException;
import io.flamingock.internal.core.engine.audit.recovery.RecoveryIssue;
import io.flamingock.internal.core.pipeline.actions.ChangeAction;
import io.flamingock.internal.core.pipeline.actions.ChangeActionMap;
import io.flamingock.internal.core.pipeline.execution.ExecutableStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates stages and pipelines for execution readiness.
 * This component provides generic validation capabilities that can be extended
 * for different types of execution validation requirements.
 */
public class ExecutionValidator {

    /**
     * Validates that a stage is ready for execution by checking for any
     * actions that require manual intervention.
     * 
     * @param actionPlan the action plan for the stage
     * @param stageName the name of the stage for error reporting
     * @throws ManualInterventionRequiredException if any changes require manual intervention
     */
    public static void validateStageForExecution(ChangeActionMap actionPlan, String stageName) {
        List<RecoveryIssue> recoveryIssues = new ArrayList<>();
        
        Map<String, ChangeAction> actionMap = actionPlan.getActionMap();
        
        for (Map.Entry<String, ChangeAction> entry : actionMap.entrySet()) {
            if (entry.getValue() == ChangeAction.MANUAL_INTERVENTION) {
                recoveryIssues.add(new RecoveryIssue(entry.getKey()));
            }
        }
        
        if (!recoveryIssues.isEmpty()) {
            throw new ManualInterventionRequiredException(recoveryIssues, stageName);
        }
    }
    
    /**
     * Validates that an executable stage is ready for execution.
     * This can be extended in the future for additional validation rules.
     * 
     * @param executableStage the stage to validate
     * @throws ManualInterventionRequiredException if validation fails
     */
    public static void validateExecutableStage(ExecutableStage executableStage) {
        // For now, we don't have additional validation at the ExecutableStage level
        // since the action validation happens at the planning stage.
        // This method is a placeholder for future validation requirements.
        
        // Future validations could include:
        // - Resource availability checks
        // - Dependency validation  
        // - Security permission checks
        // - Custom business rule validation
    }
    
    /**
     * Validates multiple stages in a pipeline for execution readiness.
     * 
     * @param actionPlans map of stage names to their action plans
     * @throws ManualInterventionRequiredException if any stage requires manual intervention
     */
    public static void validatePipelineForExecution(Map<String, ChangeActionMap> actionPlans) {
        for (Map.Entry<String, ChangeActionMap> entry : actionPlans.entrySet()) {
            validateStageForExecution(entry.getValue(), entry.getKey());
        }
    }
    
    /**
     * Checks if a stage action plan contains any manual intervention actions.
     * This is a non-throwing check that can be used for conditional logic.
     * 
     * @param actionPlan the action plan to check
     * @return true if manual intervention is required, false otherwise
     */
    public static boolean requiresManualIntervention(ChangeActionMap actionPlan) {
        return actionPlan.getActionMap().values().contains(ChangeAction.MANUAL_INTERVENTION);
    }
    
    /**
     * Counts the number of changes requiring manual intervention in a stage.
     * 
     * @param actionPlan the action plan to analyze
     * @return the count of changes requiring manual intervention
     */
    public static int getManualInterventionCount(ChangeActionMap actionPlan) {
        int count = 0;
        for (ChangeAction action : actionPlan.getActionMap().values()) {
            if (action == ChangeAction.MANUAL_INTERVENTION) {
                count++;
            }
        }
        return count;
    }
}