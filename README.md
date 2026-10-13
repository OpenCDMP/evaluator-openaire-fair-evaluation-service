# OpenCDMP OpenAIRE FAIR evaluator

**evaluator-openaire-fair-evaluation-service** assesses the assets of a plan against the [FAIR principles](https://www.go-fair.org/fair-principles/) (Findable, Accessible, Interoperable, Reusable) with the FAIR assessment service of [OpenAIRE](https://www.openaire.eu/). For every asset that has a persistent identifier, such as a DOI, OpenAIRE checks the published record behind that identifier, and the evaluator reports the outcome per asset.

It is an evaluator plugin for [OpenCDMP](https://github.com/OpenCDMP/OpenCDMP), the open platform for Data Management Plans and other Output Management Plans: a user starts an evaluation from a plan, OpenCDMP calls the evaluator over HTTP, and the result appears in OpenCDMP. The service implements the interfaces of [evaluator-base](https://github.com/OpenCDMP/evaluator-base); its evaluator id is `openaire-fair`. See [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-openaire-fair) in the OpenCDMP documentation.

## What it checks

- OpenCDMP sends the plan's RDA JSON export, produced by the [RDA JSON file transformer](https://github.com/OpenCDMP/file-transformer-rda-json). Each asset of the plan is one dataset in that export.
- For each dataset and each selected benchmark, the evaluator posts the dataset's persistent identifier (`dataset_id`) to `<FAIR_EVALUATOR_BASE_URL>/osTrails/benchmarks/<benchmark>`. OpenAIRE runs the benchmark's tests on the record and returns one result per test.
- The benchmarks are the ones OpenAIRE lists at `<FAIR_EVALUATOR_BASE_URL>/osTrails/benchmarks/`, for example `FAIR Data V2` and `FAIR Literature V4`.
- The evaluator returns one result per asset, linked to that asset. An asset passes (rank `1`) when every test passes and fails (rank `0`) otherwise. Each test becomes a metric with its title and description, plus the link to the OpenAIRE test result and its notes as messages.
- An asset is reported as not evaluated (rank `-1`), with the reason, when it has no persistent identifier of its own, or when OpenAIRE cannot be reached or answers with a server error. Without an identifier, the RDA export falls back to the asset's internal id, which OpenAIRE cannot resolve. Deposit the asset or record its identifier, then evaluate again.
- Only plans can be evaluated, and the plan needs at least one asset. Asset evaluation on its own (`/rank/description`) is not supported.

The identifier comes from the asset field that carries the `rda.dataset.dataset_id` or `rda.dataset.dataset_id.identifier` semantic. The evaluator publishes this in its configuration, so the OpenCDMP asset template editor shows whether a template provides it. See the [RDA JSON semantics reference](https://opencdmp.github.io/optional-services/file-transformers/rda-json-semantics/).

## Requirements

- An OpenCDMP installation and its Keycloak realm. The service accepts only tokens that this realm issued with the `plugins` audience.
- Access to the OpenAIRE FAIR assessment service, for example `https://beta.services.openaire.eu`. The evaluator waits up to two minutes for each assessment.
- To build from source: Java 21 and Maven. The service runs on Spring Boot 4.0 and uses `evaluator-base`; the version is in `pom.xml`.

## Run with Docker

The image is [`opencdmp/evaluator-openaire-fair-evaluation-service`](https://hub.docker.com/r/opencdmp/evaluator-openaire-fair-evaluation-service) on Docker Hub. It listens on port `8080`, reads `app-<PROFILE>.env` from `/config` (set the `PROFILE` environment variable), and writes its logs to `/logs`. [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#step-1-run-the-service) shows a Docker Compose entry.

At startup the service downloads the OpenID configuration from `IDP_ISSUER_URI`, so Keycloak must be reachable at that address. `/actuator/health` reports when the service is up.

## Build from source

```bash
mvn clean package
```

The build produces `web/target/evaluator-fair-web-1.0.0-SNAPSHOT.jar`. To run it, set the required variables and start it:

```bash
export IDP_ISSUER_URI=https://opencdmp.example.org/auth/realms/OpenCDMP
export IDP_CLAIMS_ROLES_PATH=plugins.roles
export SECURITY_AUDIENCE=plugins
export LOGGING_PATH=./logs
export FAIR_EVALUATOR_BASE_URL=https://beta.services.openaire.eu
java -jar web/target/evaluator-fair-web-1.0.0-SNAPSHOT.jar
```

Without `SERVER_PORT`, the service listens on port `8089`; check it with `curl http://localhost:8089/actuator/health`. To call it with a token and try it with a local OpenCDMP installation, see [Run and test a plugin locally](https://opencdmp.github.io/developers/plugins/run-locally/).

The `Dockerfile` builds the service from source:

```bash
docker build -t evaluator-openaire-fair-evaluation-service .
```

## Configuration

The service reads its settings from environment variables. It needs the [settings common to all plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/#settings-common-to-all-plugin-services): `IDP_ISSUER_URI`, `IDP_CLAIMS_ROLES_PATH` (`plugins.roles`) and `SECURITY_AUDIENCE` (`plugins`) for authentication, `PROFILE`, and the logging settings. Its own settings are:

| Variable | Purpose | Default |
| --- | --- | --- |
| `FAIR_EVALUATOR_BASE_URL` | Base URL of the OpenAIRE FAIR assessment service. Required. | - |
| `FAIR_EVALUATOR_USE_REMOTE_BENCHMARKS` | Offer the benchmarks that OpenAIRE lists. When `false`, offer the `availableBenchmarks` of `fair.yml`; the bundled file lists FAIR Literature V3 and V4 and FAIR Data V2. | `true` |
| `FAIR_EVALUATOR_HAS_LOGO`, `FAIR_EVALUATOR_LOGO_PATH` | Logo shown next to the evaluator in OpenCDMP | `true`, `classpath:openaire.png` |
| `FAIR_EVALUATOR_USE_SHARED_STORAGE` | Reported to OpenCDMP, but this evaluator reads no shared folder. Leave it `false`. | `false` |

[Evaluator services configuration](https://opencdmp.github.io/deployment/configuration/backend/evaluators/#evaluator-openaire-fair) has the full reference.

### Register with OpenCDMP

Register the service with its address in the API service configuration under `evaluator.sources`, with `evaluatorId: openaire-fair` and the RDA JSON transformer (`rda-file-transformer`) as `rdaTransformerId`, or for one organisation under **Settings > Plugins**. The API service calls the evaluator with a client-credentials token that carries the `plugins` audience. See [Registering plugin services](https://opencdmp.github.io/deployment/configuration/plugin-services/).

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/evaluator/rank/plan` | Evaluate every asset of a plan against the selected benchmarks |
| `POST` | `/api/evaluator/rank/description` | Not supported; returns an error |
| `GET` | `/api/evaluator/config` | Evaluator id, available benchmarks, how ranks map to pass, fail or not evaluated, and the semantics the evaluator reads |
| `GET` | `/api/evaluator/logo` | Logo as base64, if one is configured |

The `/api` endpoints require a bearer token. `/actuator/health`, `/actuator/info`, `/actuator/metrics` and `/actuator/prometheus` are open, so keep the port on an internal network.

Request (`file` is the RDA JSON export, base64-encoded; `benchmarkIds` are the ids that `/api/evaluator/config` lists):

```json
{
  "planModel": {
    "id": "0b7f4a8e-5d3c-4a51-9f2e-6c1d2b3a4e5f",
    "label": "My research plan",
    "rdaJsonFile": {
      "filename": "plan.json",
      "mimeType": "application/json",
      "file": "<base64-encoded RDA JSON>"
    }
  },
  "benchmarkIds": ["https://beta.services.openaire.eu/osTrails/benchmarks/FAIR_Data_V2"]
}
```

The response has one entry per asset. `target` identifies the asset, and `details` holds the identifier that was assessed or the reason the asset was not assessed:

```json
[
  {
    "rank": 0.0,
    "target": { "entityType": "Description", "entityId": "<asset id>", "label": "Survey data" },
    "details": "10.5281/zenodo.1234567",
    "results": [
      {
        "rank": 0.0,
        "benchmarkTitle": "OpenAIRE Benchmark: FAIR Data V2",
        "metrics": [
          {
            "rank": 1.0,
            "metricTitle": "<title of the OpenAIRE test>",
            "metricDetails": "<description of the test>",
            "messages": [
              { "title": "URL", "message": "<link to the test result>" },
              { "title": "Notes", "message": "<log of the test>" }
            ]
          }
        ]
      }
    ]
  },
  {
    "rank": -1.0,
    "target": { "entityType": "Description", "entityId": "<asset id>", "label": "Interview transcripts" },
    "details": "This asset was not assessed because it has no persistent identifier (such as a DOI) that OpenAIRE can resolve. Deposit the asset, or record its identifier, then evaluate again."
  }
]
```

The [evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/#5-result-models) and [Common models](https://opencdmp.github.io/developers/plugins/common-models/) describe the request and result models, including targets and the not-evaluated status.

## Related resources

- [Evaluator services](https://opencdmp.github.io/optional-services/evaluator-services/#evaluator-openaire-fair): what each OpenCDMP evaluator checks
- [Evaluating plans](https://opencdmp.github.io/using/plans/evaluators/): running an evaluation and reading the result in OpenCDMP
- [Evaluator plugin guide](https://opencdmp.github.io/developers/plugins/evaluator/): building your own evaluator
- [OpenCDMP documentation](https://opencdmp.github.io/)

## Contributing

Report bugs and suggest changes in [GitHub Issues](https://github.com/OpenCDMP/evaluator-openaire-fair-evaluation-service/issues). See [Contributing](https://opencdmp.github.io/developers/contributing/) for how to propose a change.

## License

evaluator-openaire-fair-evaluation-service is licensed under the [European Union Public Licence (EUPL) v1.2](LICENSE).

## Contact

Questions: email opencdmp at cite.gr.
