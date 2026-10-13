
package org.opencdmp.evaluator.fair.model.rda;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * An RDA dataset alternate identifier.
 * <p>
 * OpenCDMP stamps one of these on every exported dataset with type {@code internal} and an
 * {@code opencdmp_} prefixed identifier, which is how a dataset in the exported file can be
 * traced back to the description it came from. The primary {@code dataset_id} cannot serve
 * that purpose: it holds whatever persistent identifier the description declares, and only
 * falls back to the description id when there is none.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlternateIdentifier implements Serializable {

    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("type")
    private String type;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
