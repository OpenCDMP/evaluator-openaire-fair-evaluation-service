package org.opencdmp.evaluator.fair.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties
public class Summary {

    private Integer validationJobId;

    private String ruleName;

    private String ruleId;

    @JsonProperty("rule_weight")
    private Integer ruleWeight;

    @JsonProperty("requirement_level")
    private String requirementLevel;

    private String description;

    @JsonProperty("fair_principles")
    private String fairPrinciples;

    @JsonProperty("link")
    private String link;

    private String guidelines;

    @JsonProperty("rule_status")
    private String ruleStatus;

    @JsonProperty("has_errors")
    private Boolean hasErrors;

    @JsonProperty("has_warnings")
    private Boolean hasWarnings;

    public Integer getValidationJobId() {
        return validationJobId;
    }

    public void setValidationJobId(Integer validationJobId) {
        this.validationJobId = validationJobId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Integer getRuleWeight() {
        return ruleWeight;
    }

    public void setRuleWeight(Integer ruleWeight) {
        this.ruleWeight = ruleWeight;
    }

    public String getRequirementLevel() {
        return requirementLevel;
    }

    public void setRequirementLevel(String requirementLevel) {
        this.requirementLevel = requirementLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFairPrinciples() {
        return fairPrinciples;
    }

    public void setFairPrinciples(String fairPrinciples) {
        this.fairPrinciples = fairPrinciples;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getGuidelines() {
        return guidelines;
    }

    public void setGuidelines(String guidelines) {
        this.guidelines = guidelines;
    }

    public String getRuleStatus() {
        return ruleStatus;
    }

    public void setRuleStatus(String ruleStatus) {
        this.ruleStatus = ruleStatus;
    }

    public Boolean getHasErrors() {
        return hasErrors;
    }

    public void setHasErrors(Boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public Boolean getHasWarnings() {
        return hasWarnings;
    }

    public void setHasWarnings(Boolean hasWarnings) {
        this.hasWarnings = hasWarnings;
    }
}
