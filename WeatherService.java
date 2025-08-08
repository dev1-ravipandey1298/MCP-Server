package com.demo.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WeatherService {

    private final RestClient restClient;

    @Autowired
    private ObjectMapper objectMapper;

    public WeatherService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.weather.gov")
                .defaultHeader("Accept", "application/geo+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (your@email.com)")
                .build();
    }

    @Tool(description = "Get weather forecast for a specific latitude/longitude")
    public String getWeatherForecastByLocation(
            double latitude,   // Latitude coordinate
            double longitude   // Longitude coordinate
    ) {
        // Returns detailed forecast including:
        // - Temperature and unit
        // - Wind speed and direction
        // - Detailed forecast description
        try {
            // Step 1: Get gridpoint URL from /points/{lat},{lon}
            String pointUrl = String.format("/points/%.4f,%.4f", latitude, longitude);
            ResponseEntity<String> pointResponse = restClient.get()
                    .uri(pointUrl)
                    .retrieve()
                    .toEntity(String.class);

            JsonNode pointJson = objectMapper.readTree(pointResponse.getBody());
            String forecastUrl = pointJson.at("/properties/forecast").asText();

            // Step 2: Get forecast data from forecast URL
            ResponseEntity<String> forecastResponse = restClient.get()
                    .uri(forecastUrl)
                    .retrieve()
                    .toEntity(String.class);

            JsonNode forecastJson = objectMapper.readTree(forecastResponse.getBody());
            JsonNode periods = forecastJson.at("/properties/periods");

            if (periods.isArray() && periods.size() > 0) {
                JsonNode firstPeriod = periods.get(0);
                String temperature = firstPeriod.get("temperature").asText();
                String tempUnit = firstPeriod.get("temperatureUnit").asText();
                String windSpeed = firstPeriod.get("windSpeed").asText();
                String windDirection = firstPeriod.get("windDirection").asText();
                String detailedForecast = firstPeriod.get("detailedForecast").asText();

                return String.format("Forecast: %s\nTemperature: %s %s\nWind: %s %s",
                        detailedForecast, temperature, tempUnit, windSpeed, windDirection);
            } else {
                return "No forecast data available.";
            }

        } catch (RestClientException | NullPointerException | IllegalArgumentException e) {
            return "Error fetching weather data: " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }
    }

    @Tool(description = "Get weather alerts for a US state")
    public String getAlerts(
            @ToolParam(description = "Two-letter US state code (e.g. CA, NY)") String state
    ) {
        // Returns active alerts including:
        // - Event type
        // - Affected area
        // - Severity
        // - Description
        // - Safety instructions

        try {
            // Construct the alerts endpoint with area filter
            String alertsUrl = String.format("/alerts/active?area=%s", state.toUpperCase());

            ResponseEntity<String> response = restClient.get()
                    .uri(alertsUrl)
                    .retrieve()
                    .toEntity(String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode features = root.get("features");

            if (features == null || !features.isArray() || features.size() == 0) {
                return "No active alerts for state: " + state.toUpperCase();
            }

            StringBuilder result = new StringBuilder();
            for (JsonNode feature : features) {
                JsonNode properties = feature.get("properties");
                String event = properties.get("event").asText();
                String areaDesc = properties.get("areaDesc").asText();
                String severity = properties.get("severity").asText();
                String description = properties.get("description").asText();
                String instruction = properties.get("instruction").asText("");

                result.append(String.format("""
                    🔔 Event: %s
                    📍 Area: %s
                    ⚠️ Severity: %s
                    📝 Description: %s
                    🛡️ Instructions: %s

                    """, event, areaDesc, severity, description, instruction.isEmpty() ? "N/A" : instruction));
            }

            return result.toString();

        } catch (RestClientException e) {
            return "Error fetching alerts: " + e.getMessage();
        } catch (Exception e) {
            return "Unexpected error: " + e.getMessage();
        }

    }

}
