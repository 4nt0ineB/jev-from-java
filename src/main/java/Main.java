import io.javalin.Javalin;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import org.eclipse.jetty.http.HttpHeader;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;


void main() {
    var client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(10))
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();
    var objectMapper = getObjectMapper();
    var app = Javalin.create(config -> {
        config.concurrency.useVirtualThreads = true;
        config.routes.get("/", ctx -> ctx.result(hello(client, objectMapper)));
    }).start(7070);
}

public enum QuestionType {
    noul
}

public record Question(QuestionType type, String instructions) {}



public record JevRequest(String state, String model, Map<String, Question> questions){}

private String hello(HttpClient client, ObjectMapper objectMapper) throws IOException, InterruptedException {
    var jevConfig = jevConfig();
    System.out.println(jevConfig);
    var body =  objectMapper.writeValueAsString(new JevRequest(
        "Help! My payouts have been failing for 3 days.",
        jevConfig.model,
        Map.of("is_urgent", new Question(QuestionType.noul, "Does this convey urgency?"))
    ));
    System.out.println(body);
    var request = HttpRequest.newBuilder()
        .uri(jevConfig.endpoint().resolve("/v1/systemone"))
        .headers("Content-Type", "application/json;charset=UTF-8")
        .setHeader(HttpHeader.AUTHORIZATION.toString(), "Bearer " + jevConfig.apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(
           body
        ))
        .build();
    var result =  client.send(request, HttpResponse.BodyHandlers.ofString()).toString();
    System.out.println(result);
    return result;
}

record JevConfig(URI endpoint, String model, String apiKey) {}

private JevConfig jevConfig() throws IOException {
    var properties = getProperties();
    var apiUrl = URI.create((String) properties.get("typesafe.api.url"));
    var apiKey = System.getenv("TYPESAFE_API_KEY");
    var apiModel = properties.getProperty("typesafe.model");
    Objects.requireNonNull(apiKey, "Typesafe api key not provided");
    return new JevConfig(apiUrl,apiModel,apiKey);
}

private Properties getProperties() throws IOException {
    String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
    String appConfigPath = rootPath + "app.properties";
    Properties props = new Properties();
    props.load(new FileInputStream(appConfigPath));
    return props;
}


private ObjectMapper getObjectMapper() {
    return JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        // to allow serialization of "empty" POJOs (no properties to serialize)
        // (without this setting, an exception is thrown in those cases)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        // to write java.util.Date, Calendar as number (timestamp):
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)

        // DeserializationFeature for changing how JSON is read as POJOs:

        // to prevent exception when encountering unknown property:
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        // to allow coercion of JSON empty String ("") to null Object value:
        .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        // StreamReadFeatures for configuring parsing settings:

        // to allow C/C++ style comments in JSON (non-standard, disabled by default)
        .configure(JsonReadFeature.ALLOW_JAVA_COMMENTS, true)
        // to allow (non-standard) unquoted field names in JSON:
        .configure(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES, true)
        // to allow use of apostrophes (single quotes), non standard
        .configure(JsonReadFeature.ALLOW_SINGLE_QUOTES, true)

        // JsonWriteFeature for configuring low-level JSON generation:

        // to force escaping of non-ASCII characters:
        .configure(JsonWriteFeature.ESCAPE_NON_ASCII, true)
        .build();
}
