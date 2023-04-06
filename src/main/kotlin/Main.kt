import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.PushGateway
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.random.Random


fun main() {
    thread(start = true) {
        while (true) {
            httpMetric.set(Random.nextDouble(-100.0, 100.0))
            httpLabeledMetric
                .labels(
                    "${Random.nextBoolean()}", "${Random.nextBoolean()}", "${Random.nextBoolean()}"
                )
                .set(Random.nextDouble(-100.0, 100.0))
            Thread.sleep(1000L)
        }
    }

    thread(start = true) {
        while (true) {
            val durationTimer = pushgatewayMetric.startTimer()
            try {
                pushgatewayMetric.set(Random.nextDouble(-100.0, 100.0))
                pushgatewayLabeledMetric
                    .labels(
                        "${Random.nextBoolean()}", "${Random.nextBoolean()}", "${Random.nextBoolean()}"
                    )
                    .set(Random.nextDouble(-100.0, 100.0))
            } finally {
                durationTimer.setDuration()
                val pg = PushGateway("127.0.0.1:9091")
                pg.pushAdd(pushgatewayRegistry, "my_batch_job")
            }
            Thread.sleep(1000L)
        }
    }

    thread(start = true) {
        while (true) {
            upDownCounter.add(
                Random.nextLong(-100, 100),
                Attributes.builder()
                    .put("label7", Random.nextBoolean().toString())
                    .put("label8", Random.nextBoolean().toString())
                    .put("label9", Random.nextBoolean().toString())
                    .build()
            )
            Thread.sleep(1000L)
        }
    }

    embeddedServer(CIO, port = 8000) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                }
            )
        }
        routing {
            get ("/-/healthy") {
                call.respondText("Exporter is Healthy.")
            }
            get ("/metrics") {
                val contentType = TextFormat.chooseContentType(context.request.header("Accept"))
                call.respondTextWriter(contentType = ContentType.parse(contentType), status = HttpStatusCode.OK) {
                    TextFormat.writeFormat(contentType, this, httpRegistry.metricFamilySamples())
                }
            }
            post("/webhook") {
                val payload = call.receiveText()
                println(payload)
            }
        }
    }.start(wait = true)
}

val httpRegistry = CollectorRegistry()
val pushgatewayRegistry = CollectorRegistry()

val httpMetric = Gauge.build()
    .name("dev_knonm_http_scraped_metric")
    .help("Metric exposed over HTTP.")
    .register(httpRegistry)!!

val httpLabeledMetric = Gauge.build()
    .name("dev_knonm_http_scraped_labeled_metric")
    .help("Metric exposed over HTTP (labeled).")
    .labelNames("labelA", "labelB", "labelC")
    .register(httpRegistry)!!

val pushgatewayMetric = Gauge.build()
    .name("dev_knonm_pushgateway_metric")
    .help("Metric exposed over pushgateway.")
    .register(pushgatewayRegistry)!!

val pushgatewayLabeledMetric = Gauge.build()
    .name("dev_knonm_pushgateway_labeled_metric")
    .help("Metric exposed over pushgateway (labeled).")
    .labelNames("label1", "label2", "label3")
    .register(httpRegistry)!!

val openTelemetry: OpenTelemetry = Resource
    .getDefault()
        .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, "logical-service-name")))
    .let { resource ->
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(
                    OtlpGrpcMetricExporter.builder()
                        .setEndpoint("http://127.0.0.1:4317")
                        .build()
                ).setInterval(Duration.ofSeconds(3L)).build()
            )
            .setResource(resource)
            .build()
    }
    .let { sdkMeterProvider ->
        OpenTelemetrySdk.builder()
            .setMeterProvider(sdkMeterProvider)
            .buildAndRegisterGlobal()
    }

val meter: Meter = openTelemetry.meterBuilder("instrumentation-library-name")
    .setInstrumentationVersion("1.0.0")
    .build()

val upDownCounter = meter
    .upDownCounterBuilder("processed_jobs")
    .setDescription("Processed jobs")
    .setUnit("1")
    .build()!!
