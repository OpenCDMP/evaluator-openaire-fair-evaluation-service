package org.opencdmp.evaluator.fair.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties
public class Benchmark {

    @JsonProperty(value="@id")
    private String id;

    @JsonProperty(value="dcterms:title")
    private String title;

    @JsonProperty(value="ftr:associatedMetric")
    private List<String> associatedMetrics;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAssociatedMetrics() {
        return associatedMetrics;
    }

    public void setAssociatedMetrics(List<String> associatedMetrics) {
        this.associatedMetrics = associatedMetrics;
    }
}
