package com.cloudbees.workflow.rest.endpoints.promote;

import java.util.List;

/**
 * @author chi
 */
public class PromoteBuild {
    public List<String> environments;
    public String message;
    public Boolean success;
}
