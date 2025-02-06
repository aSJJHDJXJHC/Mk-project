import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;


public class Main{
    private static final String BASE_URL = "http://localhost:11434";
    private static final String EMBEDDING_MODEL = "nomic-embed-text"; // Replace with the correct model name
    private static final String LLM_MODEL = "llama3.2"; // Replace with the correct LLM model name

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Step 1: Collect user input
        Map<String, String> userInput = collectUserInput(scanner);

// Step 2: Load CSV data
        List<CSVRecord> csvData1 = loadCSV("/home/fatemeh/Downloads/FoodData_Central_csv_2024-10-31(1)/FoodData_Central_csv_2024-10-31/acquisition_samples.csv");
        List<CSVRecord> csvData2= loadCSV("/home/fatemeh/Downloads/FoodData_Central_csv_2024-10-31(1)/FoodData_Central_csv_2024-10-31/agricultural_samples.csv");
        // Step 3: Calculate embeddings for CSV data
        Map<String, float[]> embeddingsMap1 = calculateEmbeddings(csvData1);
        Map<String, float[]> embeddingsMap2 = calculateEmbeddings(csvData2);

        // Combine embeddings into a single map
        Map<String, float[]> allEmbeddings = new HashMap<>();
        allEmbeddings.putAll(embeddingsMap1);
        allEmbeddings.putAll(embeddingsMap2);

        String userInputText = String.join(" ", userInput.values());
        float[] userEmbedding = new float[0];
        try {
            userEmbedding = getEmbedding(userInputText);
        } catch (IOException e) {
            System.err.println("Failed to get embedding for user input: " + e.getMessage());
            return;
        }

        List<String> similarIds = findSimilarVectors(userEmbedding, allEmbeddings, 50);

        String originalPrompt = generateOriginalPrompt(userInput);
        Map<String, CSVRecord> recordsMap = new HashMap<>();
        for (CSVRecord record : csvData1) recordsMap.put(generateKey(record), record);
        for (CSVRecord record : csvData2) recordsMap.put(generateKey(record), record);
        String enhancedPrompt = generateEnhancedPrompt(originalPrompt, similarIds, recordsMap);

        try {
            String response = sendToLLM(enhancedPrompt);
            System.out.println("Response from server: " + response);
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }

        scanner.close();
    }

    private static String generateKey(CSVRecord record) {
        StringBuilder keyBuilder = new StringBuilder();
        for (String value : record) {
            keyBuilder.append(value).append("|");
        }
        return keyBuilder.toString();
    }

    private static Map<String, String> collectUserInput(Scanner scanner) {
        Map<String, String> userInput = new HashMap<>();
        System.out.print("Enter the available ingredients you have: ");
        userInput.put("availableFoods", scanner.nextLine());

        System.out.print("Do you have any food restrictions? (e.g., No meat, No dairy, etc.): ");
        userInput.put("foodRestrictions", scanner.nextLine());

        System.out.print("Are you following a specific diet? (e.g., Vegan, Keto, etc.): ");
        userInput.put("dietType", scanner.nextLine());

        System.out.print("Do you have any food allergies? (e.g., Gluten, Nuts, Lactose, None): ");
        userInput.put("allergies", scanner.nextLine());

        System.out.print("Do you have restrictions on salt, sugar, or fat intake? ");
        userInput.put("nutritionRestrictions", scanner.nextLine());

        System.out.print("Do you prefer spicy food? (Yes/No): ");
        userInput.put("spicyPreference", scanner.nextLine());

        System.out.print("Which meal are you planning for? (Breakfast, Lunch, Dinner, Snack): ");
        userInput.put("mealType", scanner.nextLine());

        System.out.print("Do you prefer a light or heavy meal? ");
        userInput.put("mealWeight", scanner.nextLine());

        System.out.print("How much time do you have for cooking? (e.g., 15 minutes, 30 minutes, 1 hour): ");
        userInput.put("cookingTime", scanner.nextLine());

        System.out.print("What kitchen equipment do you have? (e.g., Oven, Microwave, Stove): ");
        userInput.put("cookingEquipment", scanner.nextLine());

        System.out.print("What type of food do you prefer? (e.g., Rice-based, Bread-based, Soups, etc.): ");
        userInput.put("favoriteFoods", scanner.nextLine());

        System.out.print("What flavor profile do you prefer? (Sweet, Salty, Sour, Bitter, etc.): ");
        userInput.put("tastePreference", scanner.nextLine());

        System.out.print("Are there any food combinations you dislike? ");
        userInput.put("dislikedCombinations", scanner.nextLine());

        System.out.print("Are you looking for a low-calorie or high-calorie meal? ");
        userInput.put("caloriePreference", scanner.nextLine());

        System.out.print("Do you want your meal to be high in protein? ");
        userInput.put("proteinPreference", scanner.nextLine());

        System.out.print("Do you want a fiber-rich meal? ");
        userInput.put("fiberPreference", scanner.nextLine());

        System.out.print("Do you prefer traditional or modern/fast-food meals? ");
        userInput.put("foodCulture", scanner.nextLine());

        System.out.print("Do you prefer local cuisine or international dishes? ");
        userInput.put("cuisinePreference", scanner.nextLine());

        return userInput;
    }


    private static List<CSVRecord> loadCSV(String filePath) {
        List<CSVRecord> records = new ArrayList<>();
        try (Reader reader = new FileReader(filePath)) {
            CSVFormat format = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreSurroundingSpaces(true)
                    .withTrim()
                    .withIgnoreEmptyLines(true);

            CSVParser parser = new CSVParser(reader, format);

            int lineNumber = 0;
            for (CSVRecord record : parser) {
                lineNumber++;
                try {
                    records.add(record);
                } catch (Exception ex) {
                    System.err.println("Skipping invalid record at line " + lineNumber + ": " + record);
                    ex.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + filePath);
            e.printStackTrace();
        }
        return records;
    }

    private static Map<String, float[]> calculateEmbeddings(List<CSVRecord> records) {
        Map<String, float[]> embeddingsMap = new HashMap<>();
        for (CSVRecord record : records) {
            try {
                String key = generateKey(record);

                String text = record.toString();
                float[] embedding = getEmbedding(text);
                embeddingsMap.put(key, embedding);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return embeddingsMap;
    }

    private static float[] getEmbedding(String text) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/api/embeddings").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject payload = new JSONObject();
        payload.put("model", EMBEDDING_MODEL);
        payload.put("input", text);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8);
            StringBuilder responseBuilder = new StringBuilder();
            while (scanner.hasNextLine()) {
                responseBuilder.append(scanner.nextLine());
            }
            scanner.close();
            JSONObject responseJson = new JSONObject(responseBuilder.toString());
            return parseEmbedding(responseJson);
        } else {
            throw new IOException("Failed to get embedding. Response code: " + connection.getResponseCode());
        }
    }

    private static float[] parseEmbedding(JSONObject responseJson) {
        List<Object> embeddingList = responseJson.getJSONArray("embedding").toList();
        float[] embedding = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            embedding[i] = ((Number) embeddingList.get(i)).floatValue();
        }
        return embedding;
    }
    private static List<String> findSimilarVectors(float[] userEmbedding, Map<String, float[]> embeddingsMap, int topK) {
        Map<String, Double> similarityScores = new HashMap<>();
        for (Map.Entry<String, float[]> entry : embeddingsMap.entrySet()) {
            String id = entry.getKey();
            float[] embedding = entry.getValue();
            double similarity = cosineSimilarity(userEmbedding, embedding);
            similarityScores.put(id, similarity);
        }

        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(similarityScores.entrySet());
        sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        List<String> topKIds = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sortedEntries.size()); i++) {
            topKIds.add(sortedEntries.get(i).getKey());
        }
        return topKIds;
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static String generateEnhancedPrompt(String originalPrompt, List<String> similarIds, Map<String, CSVRecord> recordsMap) {
        StringBuilder enhancedPrompt = new StringBuilder(originalPrompt);
        enhancedPrompt.append("\n\nRelevant Data:\n");
        for (String id : similarIds) {
            CSVRecord record = recordsMap.get(id);
            enhancedPrompt.append(record.toString()).append("\n");
        }
        return enhancedPrompt.toString();
    }

    private static String generateOriginalPrompt(Map<String, String> userInput) {
        return String.format(
                "You are a smart cooking assistant. Based on the following user preferences and ingredients, suggest a personalized meal idea: \n" +
                        "- Available ingredients: %s\n" +
                        "- Food restrictions: %s\n" +
                        "- Diet type: %s\n" +
                        "- Allergies: %s\n" +
                        "- Nutrition restrictions: %s\n" +
                        "- Spicy food preference: %s\n" +
                        "- Meal type: %s\n" +
                        "- Meal weight: %s\n" +
                        "- Cooking time available: %s\n" +
                        "- Available cooking equipment: %s\n" +
                        "- Favorite food types: %s\n" +
                        "- Taste preference: %s\n" +
                        "- Disliked food combinations: %s\n" +
                        "- Calorie preference: %s\n" +
                        "- Protein preference: %s\n" +
                        "- Fiber preference: %s\n" +
                        "- Food culture: %s\n" +
                        "- Cuisine preference: %s\n\n" +
                        "Please provide a detailed meal suggestion, including ingredients and simple preparation steps.",

                userInput.get("availableFoods"), userInput.get("foodRestrictions"), userInput.get("dietType"), userInput.get("allergies"),
                userInput.get("nutritionRestrictions"), userInput.get("spicyPreference"), userInput.get("mealType"),
                userInput.get("mealWeight"), userInput.get("cookingTime"), userInput.get("cookingEquipment"),
                userInput.get("favoriteFoods"), userInput.get("tastePreference"), userInput.get("dislikedCombinations"),
                userInput.get("caloriePreference"), userInput.get("proteinPreference"), userInput.get("fiberPreference"),
                userInput.get("foodCulture"), userInput.get("cuisinePreference")
        );
    }

    private static String sendToLLM(String prompt) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + "/api/generate").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        JSONObject payload = new JSONObject();
        payload.put("model", LLM_MODEL);
        payload.put("prompt", prompt);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8);
            StringBuilder responseBuilder = new StringBuilder();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                JSONObject jsonObject = new JSONObject(line);
                responseBuilder.append(jsonObject.getString("response"));
                if (jsonObject.getBoolean("done")) {
                    break;
                }
            }
            scanner.close();
            return responseBuilder.toString();
        } else {
            throw new RuntimeException("Failed to send request. Response code: " + connection.getResponseCode());
        }
    }
}
