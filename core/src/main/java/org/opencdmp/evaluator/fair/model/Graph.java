package org.opencdmp.evaluator.fair.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties
public class Graph {

    @JsonProperty(value="@id")
    private String id;

    @JsonProperty(value="@type")
    private Object type;

    @JsonProperty(value="prov:value")
    private Label value;

    @JsonProperty(value="ftr:log")
    private String log;

    @JsonProperty(value="dct:description")
    private String description;

    @JsonProperty(value="dct:title")
    private String title;

    @JsonProperty(value="dcterms:license")
    private Id license;

    @JsonProperty(value="@graph")
    private List<Graph> graphs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getType() {
        return type;
    }

    public void setType(Object type) {
        this.type = type;
    }

    public Label getValue() {
        return value;
    }

    public void setValue(Label value) {
        this.value = value;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Id getLicense() {
        return license;
    }

    public void setLicense(Id license) {
        this.license = license;
    }

    public List<Graph> getGraphs() {
        return graphs;
    }

    public void setGraphs(List<Graph> graphs) {
        this.graphs = graphs;
    }

    public static class Id {
        @JsonProperty(value="@id")
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class Label {

        @JsonProperty(value="@language")
        private String language;

        @JsonProperty(value="@value")
        private String value;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
