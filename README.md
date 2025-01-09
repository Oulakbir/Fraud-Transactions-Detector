# Fraud Detection Dashboard and Data Analysis

## Overview
This project focuses on creating a dashboard using Grafana to visualize data stored in an InfluxDB bucket named `fraud-detection`. The primary goal is to display transaction data (such as amounts and user activities) in a meaningful and interactive manner without aggregation.

---

## Project Components

### Data Source
- **Bucket Name**: `fraud-detection`
- **Measurement**: `fraud_transactions`
- **Fields**: `amount`
- **Tags**: `userId` (optional: others like `transactionType` if available)

### Tools Used
1. **InfluxDB**: Stores transaction data for querying.
2. **Flux Language**: For querying the InfluxDB data.
3. **Grafana**: Visualizes the queried data in a dashboard format.

---
## 1. Environment Setup: Docker Compose File with Kafka Cluster

 Weconfigured a Docker Compose environment to run our Kafka cluster. The configuration includes a Zookeeper service and a Kafka broker. This setup ensures a single-node Kafka cluster suitable for development and testing.
```docker
version: '3.8'

services:
  zookeeper:
    container_name: zookeeper
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - 2181:2181
    networks:
      - transactions-network

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    container_name: kafka_broker
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true" # To create topics when referenced by producer or consumer
    ports:
      - 9092:9092
    networks:
      - transactions-network

  influxdb:
    image: influxdb:2.7
    container_name: influxdb
    environment:
      DOCKER_INFLUXDB_INIT_MODE: setup
      DOCKER_INFLUXDB_INIT_USERNAME: ilham
      DOCKER_INFLUXDB_INIT_PASSWORD: 123*/456*/789*/
      DOCKER_INFLUXDB_INIT_ORG: my-org
      DOCKER_INFLUXDB_INIT_BUCKET: fraud-detection
      DOCKER_INFLUXDB_INIT_RETENTION: 1d
    ports:
      - 8086:8086
    networks:
      - transactions-network
    volumes:
      - ./influxdb:/var/lib/influxdb2

  grafana:
    image: grafana/grafana
    container_name: grafana
    depends_on:
      - influxdb
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    ports:
      - 3000:3000
    networks:
      - transactions-network
    volumes:
      - ./grafana:/var/lib/grafana

volumes:
  influxdb:
  grafana:

networks:
  transactions-network:
```

![Screenshot 2025-01-08 223832](https://github.com/user-attachments/assets/cd43a29e-0318-4128-80b3-07190bb86bef)

![Screenshot 2025-01-09 215342](https://github.com/user-attachments/assets/73b11491-7530-4bcf-9416-9468d23fa465)

---
## 2. Transaction generator Function
 Weimplemented a function to generate dummy transactions for testing purposes. Each
 transaction includes:
 ● User ID between 10000 and 99999
 ● Transaction Amount between 0 and 19999 (inclusive)
 ● Timestamp
 ```java
 // Générer des transactions aléatoires
    private static String generateTransaction(Random random) {
        int userId = 10000 + random.nextInt(90000);
        int amount = random.nextInt(20000);
        String timestamp = "2025-01-08T12:00:00Z";

        return String.format("{\"userId\":\"%d\", \"amount\":%d, \"timestamp\":\"%s\"}", userId, amount, timestamp);
    }
```
---
### 3. Transaction Producer Implementation
 The Transaction Producer application uses Kafka's producer API to send transactions to the 'transactions-input' topic.
 ```java
public static void main(String[] args) {
        // Configurer Kafka Producer
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        Random random = new Random();

        // Envoyer des messages aléatoires
        while (true) {
            try {
                String transaction = generateTransaction(random);
                producer.send(new ProducerRecord<>("transactions-input", null, transaction));
                System.out.println("Envoyé : " + transaction);

                // Pause entre chaque message
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
```
---
## 4. Transaction Processing and Fraud Detection
 The Kafka Streams application processes incoming transactions and implements fraud detection logic
 ```java
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
```
---
## 5. Kafka Topics Creation
 Before testing, We created two Kafka topics:
 ● transactions-input: for incoming raw transactions
 ● fraud-alerts: for suspicious transactions identified by the processor
 
 ![Screenshot 2025-01-08 222055](https://github.com/user-attachments/assets/48aec1ec-1519-436b-a32f-8c150f4705dd)

![Screenshot 2025-01-08 224621](https://github.com/user-attachments/assets/19302def-45fa-4632-b19f-ee54b146352c)

![Screenshot 2025-01-09 1613312](https://github.com/user-attachments/assets/e3429f15-9ab4-405b-99c2-3f2de5c96496)
---
## 6. InfluxDB Integration
 Our system's persistence layer is handled by InfluxDB, a time-series database well-suited for storing financial transaction data. The Docker Compose configuration shown includes the InfluxDB service, configured with initial setup parameters including admin
 credentials and the creation of our 'fraud_detection' bucket. The screenshot demonstrates the successful deployment of InfluxDB alongside our existing services.

 ![Screenshot 2025-01-09 230137](https://github.com/user-attachments/assets/91ebbf56-42aa-4583-8d51-20d7a920cdb5)
---
## 7. Grafana Integration and Dashboard Configuration The final component of our system is the Grafana visualization layer. The configuration shown in the Docker Compose file sets up Grafana with appropriate port mapping and
 admin credentials

 ![image](https://github.com/user-attachments/assets/d78f6ea7-88a7-44df-8b25-e0093b49955a)

---
## 8. Query Examples to test your connection

### Raw Data Query for Dashboard
This query fetches the raw transaction data:
```flux
from(bucket: "fraud-detection")
  |> range(start: -24h)  // Adjust the time range as needed
  |> filter(fn: (r) => r._measurement == "fraud_transactions")
  |> filter(fn: (r) => r._field == "amount")
```

### Filtered Query (Optional)
To display data based on specific transaction types (if applicable):
```flux
from(bucket: "fraud-detection")
  |> range(start: -24h)
  |> filter(fn: (r) => r._measurement == "fraud_transactions")
  |> filter(fn: (r) => r._field == "amount")
  |> filter(fn: (r) => r.transactionType == "payment")
```

---

## Dashboard Setup in Grafana
1. **Data Source**: Connect Grafana to InfluxDB.
2. **Panel Type**: Choose "Time Series" for visualizing transaction amounts over time.
3. **Configuration**: Add the query and adjust visualization options (e.g., axes, color, legend).
4. **Time Range**: Configure the dashboard's time range (e.g., `Last 24 hours`, `Last 7 days`).

---

## Results

### Example Visualization
1. **Time Series Chart**: Displays raw transaction amounts over time.
2. **Interactive Filtering**: Allows filtering by tags like `transactionType` or `userId` directly in the dashboard.

### Screenshot of the Dashboard

![Screenshot 2025-01-09 230528](https://github.com/user-attachments/assets/a5041cbd-5df8-4694-a7df-e2a995d06bc0)

![Screenshot 2025-01-09 230544](https://github.com/user-attachments/assets/34ededd5-602c-49c3-9844-214a1b9b9ba7)

![Screenshot 2025-01-09 231619](https://github.com/user-attachments/assets/2cef2e3d-db9a-4d35-baeb-681400bd53e2)

---

## Code Repository
All code used for this project, including Flux queries and configuration files, can be found in the repository.

---

