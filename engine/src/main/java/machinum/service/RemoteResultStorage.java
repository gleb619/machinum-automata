package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.model.ScenarioResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Remote implementation of ResultStorage that uses HTTP calls to a remote service.
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteResultStorage implements ResultStorage {

    private final String remoteApiBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public void save(ScenarioResult result) {
        try {
            String requestBody = objectMapper.writeValueAsString(result);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/results"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.debug(">> POST {}", remoteApiBaseUrl + "/api/results");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< POST {} {}", remoteApiBaseUrl + "/api/results", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("Failed to save result remotely: {}", response.body());
                throw new AppException("Failed to save result remotely, status: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error saving result remotely", e);
            throw new AppException("Error saving result remotely", e);
        }
    }

    @Override
    public List<ScenarioResult> getAll() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/results"))
                    .GET()
                    .build();

            log.debug(">> GET {}", remoteApiBaseUrl + "/api/results");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< GET {} {}", remoteApiBaseUrl + "/api/results", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("Failed to get all results remotely: {}", response.body());
                throw new AppException("Failed to get all results remotely, status: " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), new TypeReference<List<ScenarioResult>>() {
            });
        } catch (IOException | InterruptedException e) {
            log.error("Error getting all results remotely", e);
            throw new AppException("Error getting all results remotely", e);
        }
    }

    @Override
    public ScenarioResult getById(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/results/" + id))
                    .GET()
                    .build();

            log.debug(">> GET {}", remoteApiBaseUrl + "/api/results/" + id);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("<< GET {} {}", remoteApiBaseUrl + "/api/results/" + id, response.statusCode());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                log.warn("Failed to get result by ID remotely: {}", response.body());
                throw new AppException("Failed to get result by ID remotely, status: " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), ScenarioResult.class);
        } catch (IOException | InterruptedException e) {
            log.error("Error getting result by ID remotely", e);
            throw new AppException("Error getting result by ID remotely", e);
        }
    }

    @Override
    public void deleteById(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(remoteApiBaseUrl + "/api/results/" + id))
                    .DELETE()
                    .build();

            log.debug(">> DELETE {}", remoteApiBaseUrl + "/api/results/" + id);
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("<< DELETE {} {}", remoteApiBaseUrl + "/api/results/" + id, response.statusCode());

            if (response.statusCode() != 204 && response.statusCode() != 200) {
                log.warn("Failed to delete result by ID remotely, status: {}", response.statusCode());
                throw new AppException("Failed to delete result by ID remotely, status: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error deleting result by ID remotely", e);
            throw new AppException("Error deleting result by ID remotely", e);
        }
    }
}
