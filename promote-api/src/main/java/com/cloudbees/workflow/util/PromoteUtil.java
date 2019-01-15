package com.cloudbees.workflow.util;

import com.google.common.collect.ImmutableList;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.util.List;

/**
 * @author chi
 */
public class PromoteUtil {
    public static final String PROMOTE_FROM_VERSION = "PROMOTE_FROM_VERSION";
    public static final String PROMOTE_FROM_ENVIRONMENT = "PROMOTE_FROM_ENVIRONMENT";
    public static final String ENVIRONMENT = "ENVIRONMENT";

    public static List<String> environments(WorkflowJob project) {
        for (JobProperty property : project.getProperties().values()) {
            if (property instanceof ParametersDefinitionProperty) {
                ParametersDefinitionProperty parametersDefinitionProperty = (ParametersDefinitionProperty) property;
                ParameterDefinition parameterDefinition = parametersDefinitionProperty.getParameterDefinition("ENVIRONMENT");
                if (parameterDefinition instanceof ChoiceParameterDefinition) {
                    ChoiceParameterDefinition choiceParameterDefinition = (ChoiceParameterDefinition) parameterDefinition;
                    return choiceParameterDefinition.getChoices();
                }
            }
        }
        return ImmutableList.of();
    }
}
