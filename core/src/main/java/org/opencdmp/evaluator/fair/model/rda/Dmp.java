
package org.opencdmp.evaluator.fair.model.rda;

import com.fasterxml.jackson.annotation.*;
import gr.cite.tools.exception.MyApplicationException;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * The DMP Schema
 * <p>
 * 
 * 
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "dataset",
})
public class Dmp implements Serializable
{

    @JsonProperty("dataset")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Dataset> dataset = null;

    public List<Dataset> getDataset() {
        return dataset;
    }

    public void setDataset(List<Dataset> dataset) {
        this.dataset = dataset;
    }
}
