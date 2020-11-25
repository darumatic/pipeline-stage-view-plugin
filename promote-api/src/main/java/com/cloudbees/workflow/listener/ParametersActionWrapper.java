package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.ReflectUtil;
import com.google.common.base.Strings;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author chi
 */
public class ParametersActionWrapper {
    private final Logger logger = LoggerFactory.getLogger(ParametersActionWrapper.class);

    private final ParametersAction action;

    public ParametersActionWrapper(ParametersAction action) {
        this.action = action;
    }

    public ParameterValue getParameter(String key) {
        return action.getParameter(key);
    }

    public Set<String> keySet() {
        return action.getAllParameters().stream().map(item -> item.getName()).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    public void addParameter(ParameterValue parameter) {
        if (action != null) {
            try {
                Field parametersField = ReflectUtil.field(action.getClass(), "parameters");
                parametersField.setAccessible(true);
                List<ParameterValue> current = (List<ParameterValue>) parametersField.get(action);
                current.add(parameter);
            } catch (Exception e) {
                logger.error("failed to add parameter", e);
            }
        }
    }

    public void setParameter(String key, String value) {
        if (action != null) {
            try {
                Field parametersField = ReflectUtil.field(action.getClass(), "parameters");
                parametersField.setAccessible(true);
                List<ParameterValue> current = (List<ParameterValue>) parametersField.get(action);
                for (ParameterValue parameterValue : current) {
                    if (parameterValue instanceof StringParameterValue && Objects.equals(parameterValue.getName(), key)) {
                        StringParameterValue stringParameterValue = (StringParameterValue) parameterValue;
                        Field valueField = ReflectUtil.field(StringParameterValue.class, "value");
                        valueField.setAccessible(true);
                        valueField.set(stringParameterValue, value);
                    }
                }
            } catch (Exception e) {
                logger.error("failed to add parameter", e);
            }
        }
    }

    public boolean hasParameter(String name) {
        ParameterValue parameter = action.getParameter(name);
        return parameter != null && parameter.getValue() != null && !Strings.isNullOrEmpty(parameter.getValue().toString());
    }
}
