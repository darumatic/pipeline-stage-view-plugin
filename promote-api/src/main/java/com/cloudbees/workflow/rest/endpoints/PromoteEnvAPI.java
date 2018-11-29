/*
 * The MIT License
 *
 * Copyright (c) 2013-2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.workflow.rest.endpoints;

import com.cloudbees.workflow.rest.AbstractAPIActionHandler;
import com.cloudbees.workflow.util.ServeJson;
import hudson.Extension;
import hudson.model.Action;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.cloudbees.workflow.rest.endpoints.Environments.ALL;

@Extension
public class PromoteEnvAPI extends AbstractAPIActionHandler {
    @Restricted(DoNotUse.class)
    @ServeJson
    public List<String> doEnv() {
        return ALL;
    }

    @Override
    public Class type() {
        return WorkflowJob.class;
    }

    @Nonnull
    @Override
    public Collection<? extends Action> createFor(@Nonnull Object o) {
        return Collections.singletonList(new PromoteEnvAPI());
    }
}
