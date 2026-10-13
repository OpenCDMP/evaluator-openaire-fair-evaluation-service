
package org.opencdmp.evaluator.fair.model.rda;

import com.fasterxml.jackson.annotation.*;

import java.io.Serializable;
import java.util.List;


/**
 * The Dataset Items Schema
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "dataset_id",
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dataset implements Serializable
{

    /**
     * The Dataset ID Schema
     * <p>
     * Dataset ID
     * (Required)
     *
     */
    @JsonProperty("dataset_id")
    @JsonPropertyDescription("Dataset ID")
    private DatasetId datasetId;

    /**
     * Human readable name of the dataset, used as a fallback label hint for the result this
     * dataset produces.
     */
    @JsonProperty("title")
    private String title;

    /**
     * Carries the {@code opencdmp_} prefixed back-reference to the description this dataset
     * was exported from. See {@link AlternateIdentifier}.
     */
    @JsonProperty("alternate_identifier")
    private List<AlternateIdentifier> alternateIdentifier;

    public DatasetId getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(DatasetId datasetId) {
        this.datasetId = datasetId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<AlternateIdentifier> getAlternateIdentifier() {
        return alternateIdentifier;
    }

    public void setAlternateIdentifier(List<AlternateIdentifier> alternateIdentifier) {
        this.alternateIdentifier = alternateIdentifier;
    }
}
