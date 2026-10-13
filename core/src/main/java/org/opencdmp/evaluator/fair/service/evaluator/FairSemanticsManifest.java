package org.opencdmp.evaluator.fair.service.evaluator;

import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.commonmodels.enums.SemanticKeyLevel;
import org.opencdmp.commonmodels.enums.SemanticValueFormat;
import org.opencdmp.commonmodels.models.semantics.SemanticKeyUsage;
import org.opencdmp.commonmodels.models.semantics.SemanticVocabulary;
import org.opencdmp.commonmodels.models.semantics.SemanticsManifest;

import java.util.List;

/**
 * The semantics manifest the OpenAIRE FAIR evaluator publishes with its configuration.
 * <p>
 * The evaluator reads no inputs itself: it gets the plan as the RDA export the host makes before ranking, and sends
 * each dataset's identifier to OpenAIRE, which fetches and assesses the dataset behind it. So the only value that
 * matters is the one the RDA export writes as {@code dataset_id}, from the keys below. Unanswered, the export writes
 * the asset's internal id, which OpenAIRE cannot resolve, and the dataset is reported as not evaluated; that is why
 * it is "needs" although the export has a fallback. The title only labels the result.
 */
public final class FairSemanticsManifest {

    public static final String PREFIX = "rda";

    private static final SemanticsManifest MANIFEST = build();

    private FairSemanticsManifest() {
    }

    public static SemanticsManifest get() {
        return MANIFEST;
    }

    private static SemanticsManifest build() {
        SemanticKeyUsage identifier = new SemanticKeyUsage(
                List.of("rda.dataset.dataset_id", "rda.dataset.dataset_id.identifier"),
                false,
                SemanticKeyLevel.Needs,
                List.of(PluginEntityType.Description),
                "Dataset › Identifier",
                "Sent to OpenAIRE, which assesses the dataset behind it: give a resolvable DOI or URL.",
                "dmp.dataset[].dataset_id.identifier");
        identifier.setFormat(SemanticValueFormat.Uri);
        identifier.setFallback("The asset's internal id, which OpenAIRE cannot assess, so the dataset is not evaluated");

        SemanticKeyUsage title = new SemanticKeyUsage(
                List.of("rda.dataset.title"),
                false,
                SemanticKeyLevel.CanUse,
                List.of(PluginEntityType.Description),
                "Dataset › Title",
                "Labels the dataset's results.",
                "dmp.dataset[].title");
        title.setFormat(SemanticValueFormat.Text);
        title.setFallback("The asset's title");

        return new SemanticsManifest(List.of(new SemanticVocabulary(PREFIX, "RDA maDMP", true)), List.of(identifier, title));
    }
}
