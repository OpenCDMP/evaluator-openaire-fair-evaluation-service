package org.opencdmp.evaluator.fair.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResourceIdentifier {

    @JsonProperty(value = "resource_identifier")
    private String resourceIdentifier;

    public ResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }
}
