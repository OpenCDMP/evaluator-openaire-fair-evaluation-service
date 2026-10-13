
package org.opencdmp.evaluator.fair.model.rda;

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;


/**
 * Wraps the root "dmp" object of an RDA-DMP JSON file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DmpWrapper implements Serializable
{

    @JsonProperty("dmp")
    private Dmp dmp;

    public Dmp getDmp() {
        return dmp;
    }

    public void setDmp(Dmp dmp) {
        this.dmp = dmp;
    }
}
