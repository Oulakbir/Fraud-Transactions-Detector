package ma.enset;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.Random;

public class KafkaTransactionProducer {
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

    // Générer des transactions aléatoires
    private static String generateTransaction(Random random) {
        int userId = 10000 + random.nextInt(90000);
        int amount = random.nextInt(20000);
        String timestamp = "2025-01-08T12:00:00Z";

        return String.format("{\"userId\":\"%d\", \"amount\":%d, \"timestamp\":\"%s\"}", userId, amount, timestamp);
    }
}
