package org.opencdmp.evaluator.fair.service.evaluator;


public class DepositRepositoryConfiguration {

	private String repositoryId;

	private String resourceIdentifierUrl;

	private String placeholder;

	public String getRepositoryId() {
		return repositoryId;
	}

	public void setRepositoryId(String repositoryId) {
		this.repositoryId = repositoryId;
	}

	public String getResourceIdentifierUrl() {
		return resourceIdentifierUrl;
	}

	public void setResourceIdentifierUrl(String resourceIdentifierUrl) {
		this.resourceIdentifierUrl = resourceIdentifierUrl;
	}

	public String getPlaceholder() {
		return placeholder;
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}
}
