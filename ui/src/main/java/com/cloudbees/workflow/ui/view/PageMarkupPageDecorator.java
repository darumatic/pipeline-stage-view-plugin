package com.cloudbees.workflow.ui.view;

import hudson.Extension;
import hudson.model.PageDecorator;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class PageMarkupPageDecorator extends PageDecorator {
    private String headerHtmlFragment;

    public PageMarkupPageDecorator() {
        super(PageMarkupPageDecorator.class);

        headerHtmlFragment = "function prodConfirmDomReady(fn) {\n" +
            "  document.addEventListener(\"DOMContentLoaded\", fn);\n" +
            "  if (document.readyState === \"interactive\" || document.readyState === \"complete\") {\n" +
            "    fn();\n" +
            "  }\n" +
            "}\n" +
            "\n" +
            "prodConfirmDomReady(function() {\n" +
            "  let form = document.querySelector(\"#main-panel form\");\n" +
            "  if (form && form.getAttribute(\"action\").startsWith(\"build\")) {\n" +
            "    form.addEventListener(\"submit\", function(e) {\n" +
            "      let input = form.querySelector(\"input[value='ENVIRONMENT']\");\n" +
            "      if (input) {\n" +
            "        let select = input.nextElementSibling;\n" +
            "        if (select && select.tagName === \"SELECT\") {\n" +
            "          if (select.value === \"PROD\") {\n" +
            "            let confirmed = window.confirm(\"Are you sure to deploy the project to the PROD environment?\");\n" +
            "            if (!confirmed) {\n" +
            "              e.preventDefault();\n" +
            "              e.stopPropagation();\n" +
            "            }\n" +
            "            return confirmed;\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "      return true;\n" +
            "    });\n" +
            "  }\n" +
            "});";
    }

    @Override
    public String getDisplayName() {
        return "Confirm Prod Deployment";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) {
        return true;
    }

    public String getheaderHtmlFragment() {
        return headerHtmlFragment;
    }
}
