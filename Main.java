package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.sqlite.SQLiteDataSource;

public class Main {
    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";
    private static final String MODEL_NAME = "nomic-embed-text";
    private static final String DATABASE_URL = "jdbc:sqlite:embeddings.db";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static DataSource dataSource;

    public static void main(String[] args) {
        System.out.println("Program started...");
        setupDataSource();

        try (Connection conn = dataSource.getConnection()) {
            processCSVFiles("/home/fatemeh/Downloads/FoodData_Central_csv_2024-10-31(1)/FoodData_Central_csv_2024-10-31", conn);
            queryUserInput(conn);
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupDataSource() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(DATABASE_URL);
        dataSource = ds;
    }

    private static void processCSVFiles(String directoryPath, Connection conn) {
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Directory not found: " + directoryPath);
            return;
        }

        try {
            createTable(conn);
            List<Path> csvFiles = Files.list(Paths.get(directoryPath))
                    .filter(path -> path.toString().endsWith(".csv"))
                    .collect(Collectors.toList());

            for (Path path : csvFiles) {
                System.out.println("Processing file: " + path);
                processCSVFile(path, conn);
            }
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("SQL error: " + e.getMessage());
        }
    }

    private static void createTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS embeddings (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, vector TEXT)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static void processCSVFile(Path path, Connection conn) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<JSONArray> embeddings = getBatchEmbeddings(lines);

            for (int i = 0; i < lines.size(); i++) {
                saveEmbedding(conn, lines.get(i), embeddings.get(i).toString());
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + path + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }

    private static List<JSONArray> getBatchEmbeddings(List<String> texts) {
        List<JSONArray> embeddings = new ArrayList<>();
        List<Future<JSONArray>> futures = new ArrayList<>();

        for (String text : texts) {
            futures.add(executor.submit(() -> getEmbedding(text)));
        }

        for (Future<JSONArray> future : futures) {
            try {
                embeddings.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error fetching embedding: " + e.getMessage());
                embeddings.add(new JSONArray());
            }
        }
        return embeddings;
    }

    private static JSONArray getEmbedding(String text) {
        try {
            URL url = new URL(OLLAMA_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("model", MODEL_NAME);
            jsonPayload.put("prompt", text);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonPayload.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String response = br.lines().collect(Collectors.joining());
                    return new JSONObject(response).getJSONArray("embedding");
                }
            } else {
                System.err.println("Failed to get embedding, HTTP response code: " + connection.getResponseCode());
            }
        } catch (IOException e) {
            System.err.println("Network error while fetching embedding: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error processing embedding request: " + e.getMessage());
        }
        return new JSONArray();
    }

    private static void saveEmbedding(Connection conn, String text, String vector) {
        String sql = "INSERT INTO embeddings (text, vector) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, text);
            stmt.setString(2, vector);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving embedding: " + e.getMessage());
        }
    }

    private static void queryUserInput(Connection conn) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter a query:");
        String userInput = scanner.nextLine();

        try {
            JSONArray userEmbedding = getEmbedding(userInput);
            List<String> closestTexts = findClosestEmbeddings(conn, userEmbedding);

            // Send the retrieved texts to Ollama and get the response
            StringBuilder responseBuilder = new StringBuilder();
            for (String text : closestTexts) {
                JSONArray embeddingResponse = getEmbedding(text);
                responseBuilder.append("Response for text: ").append(text).append("\n")
                        .append("Embedding: ").append(embeddingResponse.toString()).append("\n");
            }

            System.out.println("Final response: " + responseBuilder.toString());
        } catch (Exception e) {
            System.err.println("Error fetching user query embedding: " + e.getMessage());
        }
    }

    private static List<String> findClosestEmbeddings(Connection conn, JSONArray userEmbedding) throws SQLException {
        List<String> closestTexts = new ArrayList<>();

        String sql = "SELECT id, text, vector FROM embeddings";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            Map<Integer, Double> distances = new HashMap<>();
            List<Map.Entry<Integer, Double>> sortedEntries = new ArrayList<>();

            while (rs.next()) {
                String storedVectorStr = rs.getString("vector");
                JSONArray storedVector = new JSONArray(storedVectorStr);

                double distance = calculateCosineDistance(userEmbedding, storedVector);
                distances.put(rs.getInt("id"), distance);
            }

            // Sort the distances to get the 50 closest vectors
            distances.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(50)
                    .forEach(entry -> {
                        try {
                            // Ensure we query text data while ResultSet is still open
                            String text = getTextFromId(conn, entry.getKey());
                            closestTexts.add(text);
                        } catch (SQLException e) {
                            System.err.println("Error fetching text for closest embedding: " + e.getMessage());
                        }
                    });
        }
        return closestTexts;
    }

    private static String getTextFromId(Connection conn, int id) throws SQLException {
        String text = "";
        String sql = "SELECT text FROM embeddings WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    text = rs.getString("text");
                }
            }
        }
        return text;
    }

    private static double calculateCosineDistance(JSONArray userEmbedding, JSONArray storedEmbedding) {
        // Assuming both embeddings are of equal length and represent vector data
        double dotProduct = 0.0;
        double userNorm = 0.0;
        double storedNorm = 0.0;

        for (int i = 0; i < userEmbedding.length(); i++) {
            double userValue = userEmbedding.getDouble(i);
            double storedValue = storedEmbedding.getDouble(i);
            dotProduct += userValue * storedValue;
            userNorm += userValue * userValue;
            storedNorm += storedValue * storedValue;
        }

        return 1.0 - (dotProduct / (Math.sqrt(userNorm) * Math.sqrt(storedNorm)));
    }
}
