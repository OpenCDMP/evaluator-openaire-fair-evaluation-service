package org.opencdmp.evaluator.fair.service.evaluator;


import gr.cite.tools.exception.MyApplicationException;
import gr.cite.tools.exception.MyValidationException;
import gr.cite.tools.logging.LoggerService;
import gr.cite.tools.logging.MapLogEntry;
import io.netty.channel.ChannelOption;
import org.opencdmp.commonmodels.enums.PluginEntityType;
import org.opencdmp.commonmodels.models.FileEnvelopeModel;
import org.opencdmp.evaluator.fair.common.JsonHandlingService;
import org.opencdmp.evaluator.fair.model.*;
import org.opencdmp.evaluator.fair.model.rda.AlternateIdentifier;
import org.opencdmp.evaluator.fair.model.rda.Dataset;
import org.opencdmp.evaluator.fair.model.rda.Dmp;
import org.opencdmp.evaluator.fair.model.rda.DmpWrapper;
import org.opencdmp.evaluatorbase.enums.RankType;
import org.opencdmp.evaluatorbase.enums.SuccessStatus;
import org.opencdmp.evaluatorbase.interfaces.BenchmarkConfiguration;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorClient;
import org.opencdmp.evaluatorbase.interfaces.EvaluatorConfiguration;
import org.opencdmp.evaluatorbase.interfaces.SelectionConfiguration;
import org.opencdmp.evaluatorbase.models.misc.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequestScope
public class EvaluatorService implements EvaluatorClient {

    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(EvaluatorService.class));
    private final EvaluatorFairServiceProperties evaluatorFairServiceProperties;
    private final ResourceLoader resourceLoader;
    private final MessageSource messageSource;
    private final JsonHandlingService jsonHandlingService;

    private static final String REPORT_IN_PROGRESS = "IN_PROGRESS";
    private static final String REPORT_STOOPED = "STOPPED";

    /** Rank meaning "assessed and compliant". */
    private static final int RANK_PASS = 1;
    /** Rank meaning "assessed and not compliant". */
    private static final int RANK_FAIL = 0;
    /**
     * Rank meaning "not assessed at all", mapped to {@link SuccessStatus#NotEvaluated} by the
     * selection configuration this evaluator publishes. Distinct from {@link #RANK_FAIL}
     * because an asset we could not look up has not failed anything.
     */
    private static final int RANK_NOT_EVALUATED = -1;

    /** Marks the alternate identifier OpenCDMP stamps on each exported dataset. */
    private static final String OPENCDMP_ALTERNATE_IDENTIFIER_TYPE = "internal";
    private static final String OPENCDMP_ALTERNATE_IDENTIFIER_PREFIX = "opencdmp_";

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** OpenAIRE runs every test of a benchmark before answering, which can take a while. */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(2);


    private byte[] logo;

    @Autowired
    public EvaluatorService(ResourceLoader resourceLoader, EvaluatorFairServiceProperties evaluatorFairServiceProperties, MessageSource messageSource, JsonHandlingService jsonHandlingService) {
        this.evaluatorFairServiceProperties = evaluatorFairServiceProperties;
        this.resourceLoader = resourceLoader;
        this.messageSource = messageSource;
        this.jsonHandlingService = jsonHandlingService;
    }


    @Override
    public List<RankResultModel> rankPlan(PlanEvaluationModel planEvaluationModel) {

        List<RankResultModel> rankResultModels = new ArrayList<>();

        if (planEvaluationModel == null || planEvaluationModel.getPlanModel() == null || planEvaluationModel.getPlanModel().getRdaJsonFile() == null || planEvaluationModel.getPlanModel().getRdaJsonFile().getFile() == null) throw new MyValidationException(this.messageSource.getMessage("rda_json_missing", new Object[]{}, LocaleContextHolder.getLocale()));

        if (planEvaluationModel.getBenchmarkIds() == null || planEvaluationModel.getBenchmarkIds().isEmpty() || planEvaluationModel.getBenchmarkIds().stream().anyMatch(x -> x == null || x.isBlank())) throw new MyValidationException(this.messageSource.getMessage("benchmark_not_found", new Object[]{}, LocaleContextHolder.getLocale()));

        FileEnvelopeModel fileEnvelopeModel = planEvaluationModel.getPlanModel().getRdaJsonFile();
        byte[] fileBytes = fileEnvelopeModel.getFile();
        String jsonContent = new String(fileBytes, StandardCharsets.UTF_8);

        if (jsonContent.isBlank()) throw new MyValidationException(this.messageSource.getMessage("rda_json_missing", new Object[]{}, LocaleContextHolder.getLocale()));

        DmpWrapper dmpWrapper = this.jsonHandlingService.fromJsonSafe(DmpWrapper.class, jsonContent);
        Dmp dmp = dmpWrapper != null ? dmpWrapper.getDmp() : null;
        if (dmp == null) throw new MyValidationException(this.messageSource.getMessage("rda_json_missing", new Object[]{}, LocaleContextHolder.getLocale()));
        if (dmp.getDataset() == null || dmp.getDataset().isEmpty()) throw new MyValidationException(this.messageSource.getMessage("rda_no_datasets", new Object[]{}, LocaleContextHolder.getLocale()));

        // Every dataset is reported on, including ones that cannot be assessed. Dropping those
        // silently used to leave the caller unable to tell a compliant plan from a plan whose
        // assets were never looked at.
        List<Dataset> datasets = dmp.getDataset();

        for (Dataset dataset: datasets) {

            UUID descriptionId = this.resolveDescriptionId(dataset);

            RankResultModel rankModel = new RankResultModel();
            rankModel.setRank(RANK_PASS);
            rankModel.setTarget(this.buildTarget(descriptionId, dataset));

            // The RDA export falls back to the description id when a description declares no
            // persistent identifier of its own. That is not something OpenAIRE can resolve, so
            // there is nothing to assess and saying "failed" would misreport the asset.
            String doi = dataset.getDatasetId() == null ? null : dataset.getDatasetId().getIdentifier();
            if (doi == null || doi.isBlank() || (descriptionId != null && descriptionId.toString().equalsIgnoreCase(doi))) {
                rankModel.setRank(RANK_NOT_EVALUATED);
                rankModel.setDetails(this.messageSource.getMessage("identifier_missing", new Object[]{}, LocaleContextHolder.getLocale()));
                rankResultModels.add(rankModel);
                continue;
            }

            rankModel.setDetails(doi);

            List<EvaluationResultModel> results = new ArrayList<>();
            String unavailableReason = null;
            for (String benchmarkId: planEvaluationModel.getBenchmarkIds()) {

                String benchmarkTile;
                if (this.evaluatorFairServiceProperties.isUseRemoteBenchmarks()) {
                    Benchmark benchmark;
                    try {
                        benchmark = this.getBenchmarkFromApi(benchmarkId);
                    } catch (WebClientException e) {
                        unavailableReason = this.unavailableReason(e, doi);
                        break;
                    }
                    if (benchmark == null) throw new MyValidationException(this.messageSource.getMessage("benchmark_not_found", new Object[]{}, LocaleContextHolder.getLocale()));
                    benchmarkTile = benchmark.getTitle();
                } else if (!this.evaluatorFairServiceProperties.getAvailableBenchmarks().isEmpty()){
                    BenchmarkConfiguration benchmark = this.evaluatorFairServiceProperties.getAvailableBenchmarks().stream().filter(x -> x.getId().equals(benchmarkId)).findFirst().orElse(null);
                    if (benchmark == null)  throw new MyApplicationException("not found benchmark: " + benchmarkId);
                    benchmarkTile = benchmark.getLabel();
                } else {
                    benchmarkTile = "";
                }

                String[] segments = benchmarkId.split("/");
                String url = "/osTrails/benchmarks/" +  segments[segments.length - 1];
                Graph response;
                try {
                    response = this.getWebClient().post()
                            .uri(url)
                            .accept(MediaType.ALL)
                            .bodyValue(new ResourceIdentifier(doi))
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Graph>() {})
                            .block();
                } catch (WebClientException e) {
                    unavailableReason = this.unavailableReason(e, doi);
                    break;
                }

                if (response == null || response.getGraphs() == null || response.getGraphs().isEmpty()) throw new MyApplicationException("response not found for benchmark with id " + benchmarkId + " and doi: " + doi);

                EvaluationResultModel evaluationResultModel = new EvaluationResultModel();
                evaluationResultModel.setBenchmarkTitle(benchmarkTile);
                evaluationResultModel.setRank(RANK_PASS);

                List<Graph> testResults = response.getGraphs().stream().filter(graph -> graph.getType() instanceof String result && result.equalsIgnoreCase("ftr:TestResult")).toList();

                if (testResults.isEmpty()) throw new MyApplicationException("response not found for benchmark with id " + benchmarkId + " and doi: " + doi);

                List<EvaluationResultMetricModel> evaluationResultMetricModels = new ArrayList<>();

                for (Graph result: testResults) {
                    EvaluationResultMetricModel evaluationResultMetricModel = new EvaluationResultMetricModel();
                    if (result.getValue() != null && result.getValue().getValue() != null && result.getValue().getValue().equalsIgnoreCase("pass")) {
                        // evaluation result for specific metric
                        evaluationResultMetricModel.setRank(RANK_PASS);
                    } else {
                        // entire evaluation result failed
                        evaluationResultModel.setRank(RANK_FAIL);
                        rankModel.setRank(RANK_FAIL);

                        // evaluation result failed for specific metric
                        evaluationResultMetricModel.setRank(RANK_FAIL);
                    }

                    evaluationResultMetricModel.setMetricTitle(result.getTitle());
                    evaluationResultMetricModel.setMetricDetails(result.getDescription());

                    List<EvaluationResultMessageModel> messages = new ArrayList<>();

                    if (result.getId() != null && !result.getId().isBlank()) messages.add(new EvaluationResultMessageModel("URL", result.getId()));
//                    if (summary.getRequirementLevel() != null && !summary.getRequirementLevel().isBlank()) messages.add(new EvaluationResultMessageModel("Requirement Level", summary.getRequirementLevel()));
                    if (result.getLog() != null && !result.getLog().isBlank() && !result.getLog().equalsIgnoreCase("No log")) messages.add(new EvaluationResultMessageModel("Notes", result.getLog()));

                    if (!messages.isEmpty()) evaluationResultMetricModel.setMessages(messages);

                    evaluationResultMetricModels.add(evaluationResultMetricModel);
                }

                evaluationResultModel.setMetrics(evaluationResultMetricModels);
                results.add(evaluationResultModel);

//                if (response.getProgress().equals(REPORT_STOOPED) && response.getExceptionMessage() != null) {
//                    evaluationResultModel.setRank(RANK_FAIL);
//                    evaluationResultModel.setBenchmarkDetails(response.getExceptionMessage());
//                } else {
//                    String summaryURL = "/validator/reports/getSummaryFromDB?jobId=" + response.getId();
//                    List<Summary> summaries = this.getWebClient().get()
//                            .uri(summaryURL)
//                            .accept(MediaType.ALL)
//                            .retrieve()
//                            .bodyToMono(new ParameterizedTypeReference<List<Summary>>() {})
//                            .block();
//
//                    if (summaries == null) throw new MyApplicationException("no summary found for benchmark with id " + benchmarkId + " and baseUrl: " + baseUrl + " and jobId: " + response.getId());
//
//                    List<EvaluationResultMetricModel> evaluationResultMetricModels = new ArrayList<>();
//                    for (Summary summary: summaries) {
//                        EvaluationResultMetricModel evaluationResultMetricModel = new EvaluationResultMetricModel();
//                        if (summary.getRuleStatus() != null && summary.getRuleStatus().equalsIgnoreCase("SUCCESS")) {
//                            // evaluation result for specific metric
//                            evaluationResultMetricModel.setRank(RANK_PASS);
//                        } else {
//                            // entire evaluation result failed
//                            evaluationResultModel.setRank(RANK_FAIL);
//                            rankModel.setRank(RANK_FAIL);
//
//                            // evaluation result failed for specific metric
//                            evaluationResultMetricModel.setRank(RANK_FAIL);
//                        }
//
//                        evaluationResultMetricModel.setMetricTitle(summary.getRuleName());
//                        evaluationResultMetricModel.setMetricDetails(summary.getDescription());
//
//                        List<EvaluationResultMessageModel> messages = new ArrayList<>();
//
//                        if (summary.getLink() != null && !summary.getLink().isBlank()) messages.add(new EvaluationResultMessageModel("URL", summary.getLink()));
//                        if (summary.getRequirementLevel() != null && !summary.getRequirementLevel().isBlank()) messages.add(new EvaluationResultMessageModel("Requirement Level", summary.getRequirementLevel()));
//                        if (summary.getGuidelines() != null && !summary.getGuidelines().isBlank()) messages.add(new EvaluationResultMessageModel("Guidelines", summary.getGuidelines()));
//
//                        if (!messages.isEmpty()) evaluationResultMetricModel.setMessages(messages);
//
//                        evaluationResultMetricModels.add(evaluationResultMetricModel);
//                    }
//
//                    evaluationResultModel.setMetrics(evaluationResultMetricModels);
//                    results.add(evaluationResultModel);
//                }


            }

            if (unavailableReason != null) {
                rankModel.setRank(RANK_NOT_EVALUATED);
                rankModel.setDetails(this.messageSource.getMessage("evaluation_unavailable", new Object[]{doi, unavailableReason}, LocaleContextHolder.getLocale()));
            } else if (!results.isEmpty()) rankModel.setResults(results);
            rankResultModels.add(rankModel);
        }

        return rankResultModels;
    }

    /**
     * Returns the reason to report when OpenAIRE could not be reached or failed on its side.
     * Any other error response is rethrown, since it is not an outage.
     */
    private String unavailableReason(WebClientException e, String doi) {
        if (e instanceof WebClientResponseException responseException && !responseException.getStatusCode().is5xxServerError()) throw e;
        logger.warn("OpenAIRE could not evaluate identifier {}: {}", doi, e.getMessage());
        return e.getMessage();
    }

    /**
     * Recovers the id of the description a dataset was exported from, by reading the
     * {@code opencdmp_} prefixed alternate identifier the RDA export stamps on it.
     * <p>
     * This is the only reliable join back to the plan. {@code dataset_id} holds whatever
     * persistent identifier the description declares and matches the description id only when
     * there is none, so it cannot be used to identify the asset.
     */
    private UUID resolveDescriptionId(Dataset dataset) {
        if (dataset == null || dataset.getAlternateIdentifier() == null) return null;

        for (AlternateIdentifier alternateIdentifier : dataset.getAlternateIdentifier()) {
            if (alternateIdentifier == null || alternateIdentifier.getIdentifier() == null) continue;
            if (alternateIdentifier.getType() == null || !alternateIdentifier.getType().equalsIgnoreCase(OPENCDMP_ALTERNATE_IDENTIFIER_TYPE)) continue;
            if (!alternateIdentifier.getIdentifier().startsWith(OPENCDMP_ALTERNATE_IDENTIFIER_PREFIX)) continue;

            String raw = alternateIdentifier.getIdentifier().substring(OPENCDMP_ALTERNATE_IDENTIFIER_PREFIX.length());
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException e) {
                logger.warn("alternate identifier {} is not a usable description id", alternateIdentifier.getIdentifier());
            }
        }
        return null;
    }

    private EvaluationTargetModel buildTarget(UUID descriptionId, Dataset dataset) {
        if (descriptionId == null) return null;
        // The label is only a hint. OpenCDMP resolves its own and ignores this when it can.
        return new EvaluationTargetModel(PluginEntityType.Description, descriptionId, dataset == null ? null : dataset.getTitle());
    }

    private EvaluationResultMetricModel createEvaluationResultMetric(Graph response, EvaluationResultModel evaluationResultModel, RankResultModel rankModel) {

        if (response.getType() == null) return null;

        EvaluationResultMetricModel evaluationResultMetricModel = new EvaluationResultMetricModel();

//        if (this.validateTestType(response)) {
//            if (response.getValue() != null && response.getValue().equalsIgnoreCase("success")) {
//                // evaluation result for specific metric
//                evaluationResultMetricModel.setRank(1);
//            } else {
//                // entire evaluation result failed
//                evaluationResultModel.setRank(0);
//                rankModel.setRank(0);
//
//                // evaluation result failed for specific metric
//                evaluationResultMetricModel.setRank(0);
//            }
//        } else {
//            // evaluation result for specific metric (-1 means that has no rank)
//            evaluationResultMetricModel.setRank(-1);
//        }

        evaluationResultMetricModel.setMetricTitle(response.getId());
        evaluationResultMetricModel.setMetricDetails(response.getTitle());

        List<EvaluationResultMessageModel> messages = new ArrayList<>();

        if (response.getType() instanceof String) messages.add(new EvaluationResultMessageModel("Type", (String) response.getType()));
        else if (response.getType() instanceof List<?>) {
            String result = ((List<?>) response.getType())
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            messages.add(new EvaluationResultMessageModel("Type", result));
        }

        if (response.getDescription() != null && !response.getDescription().isBlank()) messages.add(new EvaluationResultMessageModel("Description", response.getDescription()));
//        if (response.getRdfsLabel() != null && !response.getRdfsLabel().isBlank()) messages.add(new EvaluationResultMessageModel("Rdfs Label", response.getRdfsLabel()));
//        if (response.getCreator() != null && !response.getCreator().isBlank()) messages.add(new EvaluationResultMessageModel("Creator", response.getCreator()));
        if (response.getLicense() != null && response.getLicense().getId() != null && !response.getLicense().getId().isBlank()) messages.add(new EvaluationResultMessageModel("Licence", response.getLicense().getId()));
        if (response.getLog() != null && !response.getLog().isBlank()) messages.add(new EvaluationResultMessageModel("Log", response.getLog()));

        if (!messages.isEmpty()) evaluationResultMetricModel.setMessages(messages);
        return evaluationResultMetricModel;
    }

    private boolean validateTestType(Graph response) {
        if (response.getType() instanceof String) {
            if ((((String) response.getType()).equalsIgnoreCase("ftr:Test")) || ((String) response.getType()).equalsIgnoreCase("ftr:TestResult")) {
                return true;
            }
        }
        if (response.getType() instanceof List<?>) {
            if ((((List<?>) response.getType()).contains("ftr:Test") || ((List<?>) response.getType()).contains("ftr:TestResult"))){
                return true;
            }
        }
        return false;
    }

    @Override
    public List<RankResultModel> rankDescription(DescriptionEvaluationModel descriptionEvaluationModel) {
        throw new UnsupportedOperationException("rank description not supported");
    }

    @Override
    public EvaluatorConfiguration getConfiguration() {
        EvaluatorConfiguration evaluatorConfiguration = new EvaluatorConfiguration();
        evaluatorConfiguration.setEvaluatorId(evaluatorFairServiceProperties.getEvaluatorId());
        evaluatorConfiguration.setEvaluatorEntityTypes(List.of(PluginEntityType.Plan));
        evaluatorConfiguration.setUseSharedStorage(evaluatorFairServiceProperties.isUseSharedStorage());
        evaluatorConfiguration.setHasLogo(this.evaluatorFairServiceProperties.getHasLogo());
        evaluatorConfiguration.setConfigurationFields(this.evaluatorFairServiceProperties.getConfigurationFields());
        evaluatorConfiguration.setUserConfigurationFields(this.evaluatorFairServiceProperties.getUserConfigurationFields());
        evaluatorConfiguration.setRankConfig(new RankConfig());
        evaluatorConfiguration.getRankConfig().setRankType(RankType.Selection);
        evaluatorConfiguration.getRankConfig().setSelectionConfiguration(new SelectionConfiguration());
        SelectionConfiguration.ValueSet valueSetSuccess = new SelectionConfiguration.ValueSet();
        valueSetSuccess.setKey(RANK_PASS);
        valueSetSuccess.setSuccessStatus(SuccessStatus.Pass);

        SelectionConfiguration.ValueSet valueSetFail = new SelectionConfiguration.ValueSet();
        valueSetFail.setKey(RANK_FAIL);
        valueSetFail.setSuccessStatus(SuccessStatus.Fail);

        // Without this entry the host cannot map a skipped result and rejects the whole report.
        SelectionConfiguration.ValueSet valueSetNotEvaluated = new SelectionConfiguration.ValueSet();
        valueSetNotEvaluated.setKey(RANK_NOT_EVALUATED);
        valueSetNotEvaluated.setSuccessStatus(SuccessStatus.NotEvaluated);

        evaluatorConfiguration.getRankConfig().getSelectionConfiguration().setValueSetList(Arrays.asList(valueSetSuccess, valueSetFail, valueSetNotEvaluated));

        evaluatorConfiguration.setAvailableBenchmarks(this.getAvailableBenchmarks());
        evaluatorConfiguration.setSemantics(FairSemanticsManifest.get());
        return evaluatorConfiguration;
    }

    private List<BenchmarkConfiguration> getAvailableBenchmarks() {
        if (this.evaluatorFairServiceProperties.isUseRemoteBenchmarks()) {
            List<String> benchmarkIds = this.getWebClient().get()
                    .uri("/osTrails/benchmarks/")
                    .accept(MediaType.ALL)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                    .block();

            if (benchmarkIds == null) throw new MyApplicationException("benchmarks not found");

            List<BenchmarkConfiguration> benchmarkConfigurations = new ArrayList<>();

            for (String benchmarkId: benchmarkIds) {

                Benchmark benchmark = this.getBenchmarkFromApi(benchmarkId);
                if (benchmark == null) continue;

                BenchmarkConfiguration benchmarkConfiguration = new BenchmarkConfiguration();

                benchmarkConfiguration.setId(benchmark.getId());
                benchmarkConfiguration.setLabel(benchmark.getTitle());
                benchmarkConfiguration.setAppliesTo(List.of(PluginEntityType.Plan));

                benchmarkConfigurations.add(benchmarkConfiguration);
            }
            return benchmarkConfigurations;

        } else {
            return this.evaluatorFairServiceProperties.getAvailableBenchmarks();
        }
    }

    private Benchmark getBenchmarkFromApi(String benchmarkId) {

        URI uri;
        try {
            // if url is already encode, then do decode because web client needs decoded url
            String decoded = URLDecoder.decode(benchmarkId, StandardCharsets.UTF_8);

            uri = UriComponentsBuilder
                    .fromUriString(decoded)
                    .build()
                    .encode()
                    .toUri();
        } catch (Exception e) {
            throw new MyApplicationException("Invalid benchmark URL: " + benchmarkId, e);
        }

        Graph graph = this.getWebClient().get()
                .uri(uri)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Graph>() {})
                .block();

        if (graph == null || graph.getGraphs() == null || graph.getGraphs().isEmpty()) throw new MyApplicationException("benchmark not found id: " + benchmarkId);

        for (Graph subgraph: graph.getGraphs()) {
            if (subgraph.getType() instanceof String type) {
                if (type.equalsIgnoreCase("ftr:Benchmark")) {
                    Benchmark benchmark = new Benchmark();
//                    String[] segments = benchmarkId.split("/");
//                    benchmark.setId(segments[segments.length - 1]);
                    benchmark.setId(benchmarkId);
                    benchmark.setTitle(subgraph.getTitle());
                    return benchmark;
                }
            }
        }


        return null;
    }

    @Override
    public String getLogo() {
        if(this.evaluatorFairServiceProperties != null && this.evaluatorFairServiceProperties.getHasLogo() && this.evaluatorFairServiceProperties.getLogo() != null && !this.evaluatorFairServiceProperties.getLogo().isBlank()){
            if(this.logo == null){
                try{
                    Resource resource = this.resourceLoader.getResource(this.evaluatorFairServiceProperties.getLogo());
                    if(!resource.isReadable()) return null;
                    try(InputStream inputStream = resource.getInputStream()) {
                        this.logo = inputStream.readAllBytes();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }
            return (this.logo != null && this.logo.length != 0) ? Base64.getEncoder().encodeToString(this.logo) : null;
        }
        return null;
    }

    private WebClient getWebClient(){
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder().baseUrl(this.evaluatorFairServiceProperties.getBaseUrl()).clientConnector(new ReactorClientHttpConnector(httpClient)).filters(exchangeFilterFunctions -> {
            exchangeFilterFunctions.add(logRequest());
            exchangeFilterFunctions.add(logResponse());
        }).codecs(ClientCodecConfigurer::defaultCodecs
        ).build();
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.debug(new MapLogEntry("Request").And("method", clientRequest.method().toString()).And("url", clientRequest.url().toString()));
            return Mono.just(clientRequest);
        });
    }

    private static ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().isError()) {
                return response.mutate().build().bodyToMono(String.class)
                        .flatMap(body -> {
                            logger.error(new MapLogEntry("Response").And("method", response.request().getMethod().toString()).And("url", response.request().getURI()).And("status", response.statusCode().toString()).And("body", body));
                            return Mono.just(response);
                        });
            }
            return Mono.just(response);

        });
    }
}

