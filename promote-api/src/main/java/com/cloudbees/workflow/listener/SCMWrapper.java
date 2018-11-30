package com.cloudbees.workflow.listener;

import com.cloudbees.workflow.util.ReflectUtil;
import hudson.scm.SCM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * @author chi
 */
public class SCMWrapper {
    private final Logger logger = LoggerFactory.getLogger(SCMWrapper.class);

    private final SCM scm;

    public SCMWrapper(SCM scm) {
        this.scm = scm;
    }

    public Optional<String> relativeDirectory() {
        try {
            Field extensionsField = ReflectUtil.field(scm.getClass(), "extensions");
            extensionsField.setAccessible(true);
            List extensions = ((List) extensionsField.get(scm));

            for (Object extension : extensions) {
                if (extension.getClass().getSimpleName().equals("RelativeTargetDirectory")) {
                    Field relativeTargetDirField = ReflectUtil.field(extension.getClass(), "relativeTargetDir");
                    relativeTargetDirField.setAccessible(true);
                    return Optional.of((String) relativeTargetDirField.get(extension));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.error("failed to get relative directory", e);
            return Optional.empty();
        }
    }

    public String scmName() {
        try {
            Field userRemoteConfigsField = ReflectUtil.field(scm.getClass(), "userRemoteConfigs");
            userRemoteConfigsField.setAccessible(true);
            Object userRemoteConfig = ((List) userRemoteConfigsField.get(scm)).get(0);
            Field nameField = ReflectUtil.field(userRemoteConfig.getClass(), "name");
            nameField.setAccessible(true);
            Object name = nameField.get(userRemoteConfig);

            if (name == null) {
                Field urlField = ReflectUtil.field(userRemoteConfig.getClass(), "url");
                urlField.setAccessible(true);
                String url = (String) urlField.get(userRemoteConfig);
                return projectName(url);
            }
            return (String) name;
        } catch (Exception e) {
            logger.error("failed to get scm name", e);
            return null;
        }
    }

    private String projectName(String url) {
        if (url == null) {
            return null;
        }
        int p = url.lastIndexOf("/");
        if (p > 0) {
            String name = url.substring(p + 1);
            return name.replaceAll(".git", "");
        }
        return null;
    }
}
