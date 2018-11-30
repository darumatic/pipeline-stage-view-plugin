package com.cloudbees.workflow.util;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * @author chi
 */
public class PromoteUtil {
    public static final String PROMOTE_FROM_VERSION = "PROMOTE_FROM_VERSION";
    public static final String PROMOTE_FROM_ENVIRONMENT = "PROMOTE_FROM_ENVIRONMENT";
    public static final String ENVIRONMENT = "ENVIRONMENT";
    public static final List<String> ENVIRONMENTS = Lists.newArrayList("SIT", "UAT", "PROD");
}
