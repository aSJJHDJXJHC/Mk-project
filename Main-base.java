package org.example;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.json.JSONObject;

public class Main{
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // Collect available food ingredients
        System.out.print("Enter the available ingredients you have: ");
        String availableFoods = scanner.nextLine();

        System.out.print("Do you have any food restrictions? (e.g., No meat, No dairy, etc.): ");
        String foodRestrictions = scanner.nextLine();

        // Collect dietary preferences
        System.out.print("Are you following a specific diet? (e.g., Vegan, Keto, etc.): ");
        String dietType = scanner.nextLine();

        System.out.print("Do you have any food allergies? (e.g., Gluten, Nuts, Lactose, None): ");
        String allergies = scanner.nextLine();

        System.out.print("Do you have restrictions on salt, sugar, or fat intake? ");
        String nutritionRestrictions = scanner.nextLine();

        System.out.print("Do you prefer spicy food? (Yes/No): ");
        String spicyPreference = scanner.nextLine();

        // Collect meal type
        System.out.print("Which meal are you planning for? (Breakfast, Lunch, Dinner, Snack): ");
        String mealType = scanner.nextLine();

        System.out.print("Do you prefer a light or heavy meal? ");
        String mealWeight = scanner.nextLine();

        // Cooking time and equipment
        System.out.print("How much time do you have for cooking? (e.g., 15 minutes, 30 minutes, 1 hour): ");
        String cookingTime = scanner.nextLine();

        System.out.print("What kitchen equipment do you have? (e.g., Oven, Microwave, Stove): ");
        String cookingEquipment = scanner.nextLine();

        // Food taste and preference
        System.out.print("What type of food do you prefer? (e.g., Rice-based, Bread-based, Soups, etc.): ");
        String favoriteFoods = scanner.nextLine();

        System.out.print("What flavor profile do you prefer? (Sweet, Salty, Sour, Bitter, etc.): ");
        String tastePreference = scanner.nextLine();

        System.out.print("Are there any food combinations you dislike? ");
        String dislikedCombinations = scanner.nextLine();

        // Nutrition and calorie needs
        System.out.print("Are you looking for a low-calorie or high-calorie meal? ");
        String caloriePreference = scanner.nextLine();

        System.out.print("Do you want your meal to be high in protein? ");
        String proteinPreference = scanner.nextLine();

        System.out.print("Do you want a fiber-rich meal? ");
        String fiberPreference = scanner.nextLine();

        // Cultural food preferences
        System.out.print("Do you prefer traditional or modern/fast-food meals? ");
        String foodCulture = scanner.nextLine();

        System.out.print("Do you prefer local cuisine or international dishes? ");
        String cuisinePreference = scanner.nextLine();

        // Generate a structured prompt
        String prompt = String.format(
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
                availableFoods, foodRestrictions, dietType, allergies, nutritionRestrictions, spicyPreference,
                mealType, mealWeight, cookingTime, cookingEquipment, favoriteFoods, tastePreference,
                dislikedCombinations, caloriePreference, proteinPreference, fiberPreference, foodCulture, cuisinePreference
        );

        // Create the JSON payload
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("model", "llama3.2");
        jsonPayload.put("prompt", prompt);
        jsonPayload.put("stream", false);

        try {
            // Send the request to the LLM API
            String response = sendToLLM(jsonPayload.toString());
            System.out.println("Response from server: " + response);
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }

        scanner.close();
    }

    private static String sendToLLM(String payload) throws Exception {
        String urlString = "http://localhost:11434/api/generate";
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
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
            throw new RuntimeException("Failed to send request. Response code: " + responseCode);
        }
    }
}

