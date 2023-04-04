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
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.PushGateway
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.serialization.json.Json
import kotlin.concurrent.thread
import kotlin.random.Random

fun main() {
    thread(start = true) {
        while (true) {
            for (iteration in -100..Random.nextInt(-100, 100)) {
                httpMetric.set(iteration.toDouble())
            }
            Thread.sleep(1000L)
        }
    }

    thread(start = true) {
        while (true) {
            val durationTimer = pushgatewayMetric.startTimer()
            try {
                for (iteration in -100..Random.nextInt(-100, 100)) {
                    httpMetric.set(iteration.toDouble())
                }
            } finally {
                durationTimer.setDuration()
                val pg = PushGateway("127.0.0.1:9091")
                pg.pushAdd(pushgatewayRegistry, "my_batch_job")
            }
            Thread.sleep(1000L)
        }
    }

    embeddedServer(CIO, port = 8000) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
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

val pushgatewayMetric = Gauge.build()
    .name("dev_knonm_pushgateway_metric")
    .help("Metric exposed over pushgateway.")
    .register(pushgatewayRegistry)!!
