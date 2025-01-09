package ma.enset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Predicate;

import java.time.Instant;
import java.util.Properties;

public class FraudDetectionApp {
    public static void main(String[] args) {
        // Initialize Kafka Streams
        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> transactions = builder.stream("transactions-input");

        Predicate<String, String> isFraudulent = (key, value) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode transaction = mapper.readTree(value);
                int amount = transaction.get("amount").asInt();
                return amount > 10000;
            } catch (Exception e) {
                System.err.println("Error parsing transaction JSON: " + e.getMessage());
                return false;
            }
        };

        KStream<String, String> fraudulentTransactions = transactions.filter(isFraudulent);

        // Initialize InfluxDB Client
        String token = System.getenv("SbWLf7eOPH258jaTpndSfrIkVx1WAwVwcbRbv2jH4B0Hx3RIfWjwrAKXSlCYv-rdiWOEI8m1IkmMZuVHUqb_nA==");
        String t="SbWLf7eOPH258jaTpndSfrIkVx1WAwVwcbRbv2jH4B0Hx3RIfWjwrAKXSlCYv-rdiWOEI8m1IkmMZuVHUqb_nA==";
        String org = "my-org";
        String bucket = "fraud-detection";
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create("http://localhost:8086", t.toCharArray());

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

        fraudulentTransactions.foreach((key, value) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode transaction = mapper.readTree(value);
                String userId = transaction.get("userId").asText();
                int amount = transaction.get("amount").asInt();
                Instant timestamp = Instant.now();

                // Write data using Point API
                Point point = Point.measurement("fraud_transactions")
                        .addTag("userId", userId)
                        .addField("amount", amount)
                        .time(timestamp, com.influxdb.client.domain.WritePrecision.MS);

                writeApi.writePoint(bucket, org, point);

                System.out.printf("Fraudulent transaction saved: userId=%s, amount=%d%n", userId, amount);
            } catch (Exception e) {
                System.err.println("Error writing to InfluxDB: " + e.getMessage());
            }
        });

        // Start Kafka Streams
        KafkaStreams streams = new KafkaStreams(builder.build(), getKafkaProperties());
        streams.start();

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
            influxDBClient.close();
        }));
    }

    private static Properties getKafkaProperties() {
        Properties props = new Properties();
        props.put("application.id", "fraud-detection-app");
        props.put("bootstrap.servers", "localhost:9092");
        props.put("default.key.serde", "org.apache.kafka.common.serialization.Serdes$StringSerde");
        props.put("default.value.serde", "org.apache.kafka.common.serialization.Serdes$StringSerde");
        return props;
    }
}