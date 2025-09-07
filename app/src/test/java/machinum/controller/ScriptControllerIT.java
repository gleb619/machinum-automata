package machinum.controller;

import io.jooby.Jooby;
import io.jooby.test.JoobyTest;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@JoobyTest(ScriptControllerIT.TestApp.class)
class ScriptControllerIT {

    static HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void beforeAll() {
        System.out.println("\n\n\n");
    }

    @AfterAll
    static void afterAll() {
        System.out.println("\n\n\n");
    }

    @BeforeEach
    void setUp() {
        System.out.println("\n-----\n");
    }

    @AfterEach
    void tearDown() {
        System.out.println("\n-----\n");
    }

    @Test
    void testGetAllScripts() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        // Should return empty list initially
        assertTrue(response.body().contains("[]"));
    }

    @Test
    void testCreateAndExecuteScript() throws Exception {
        // First create a script
        String scriptJson = """
                {
                    "name": "Test Script",
                    "text": "println 'Hello from test script'",
                    "timeout": 30000
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts"))
                .POST(HttpRequest.BodyPublishers.ofString(scriptJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());

        // Extract script ID from response
        String responseBody = createResponse.body();
        String scriptId = extractIdFromResponse(responseBody);

        // Execute the script
        String executeJson = """
                {
                    "params": {}
                }
                """;

        HttpRequest executeRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/" + scriptId + "/execute"))
                .POST(HttpRequest.BodyPublishers.ofString(executeJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> executeResponse = client.send(executeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, executeResponse.statusCode());

        // Verify execution result
        assertTrue(executeResponse.body().contains("success"));
    }

    @Test
    void testScriptValidation() throws Exception {
        String validationJson = """
                {
                    "code": "println 'valid groovy code'"
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/validate"))
                .POST(HttpRequest.BodyPublishers.ofString(validationJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Should return empty errors list for valid code
        assertTrue(response.body().contains("\"errors\":[]"));
    }

    @Test
    void testStaticFileServing() throws Exception {
        // Test that static HTML files are served
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/test-page.html"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Should contain the test HTML content
        assertTrue(response.body().contains("Test Page"));
        assertTrue(response.body().contains("test-link"));
    }

    //WIP, we need to work with networks. IN main process we start an app, in docker container we need to connect(but now there is no network between them)
    @Test
    @Disabled
    void testScriptCrawlingTestPage() throws Exception {
        // Create a script that crawls the test HTML page using utils and driver from binding
        String crawlingScript = """
                // Use utils to open the page
                utils.open("http://localhost:8911/test-page.html")
                
                // Wait for elements to be present
                utils.waitForElement("#test-link", 10)
                utils.waitForElement("#test-content", 10)
                
                // Find elements using utils
                def link = utils.findElement("#test-link")
                def div = utils.findElement("#test-content")
                
                // Get text and title
                def linkText = link.getText()
                def divText = div.getText()
                def pageTitle = driver.getTitle()
                
                // Use log from binding
                log.debug("Crawled page: title={}, link={}, content={}", pageTitle, linkText, divText)
                
                // Take screenshot using utils
                def screenshot = utils.takeScreenshot()
                
                // Return results
                return [
                    linkText: linkText,
                    divText: divText,
                    pageTitle: pageTitle,
                    screenshotTaken: screenshot != null
                ]
                """;

        String scriptJson = String.format("""
                {
                    "name": "Crawling Test Script",
                    "text": "%s",
                    "timeout": 30000
                }
                """, crawlingScript.replace("\n", "\\n").replace("\"", "\\\""));

        // Create script
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts"))
                .POST(HttpRequest.BodyPublishers.ofString(scriptJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());

        String scriptId = extractIdFromResponse(createResponse.body());

        // Execute script
        String executeJson = """
                {
                    "params": {}
                }
                """;

        HttpRequest executeRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/" + scriptId + "/execute"))
                .POST(HttpRequest.BodyPublishers.ofString(executeJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> executeResponse = client.send(executeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, executeResponse.statusCode());

        // Verify crawling results
        String resultBody = executeResponse.body();
        assertTrue(resultBody.contains("Test Link"));
        assertTrue(resultBody.contains("Test Content"));
        assertTrue(resultBody.contains("Test Page"));
        assertTrue(resultBody.contains("true")); // screenshotTaken
    }

    @Test
    void testScriptUsingAllBindingFeatures() throws Exception {
        // Create a comprehensive script that uses all ScenarioEngine binding features
        String comprehensiveScript = """
                // Use params from binding
                def customUrl = params.url ?: "http://localhost:8911/test-page.html"
                def waitTime = params.waitTime ?: 5
                
                // Use utils to open page
                utils.open(customUrl)
                
                // Use WebDriverWait and ExpectedConditions from binding
                def wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(waitTime))
                wait.until(ExpectedConditions.presenceOfElementLocated(By.id("test-link")))
                
                // Use Keys from binding for interactions
                def input = utils.findElement("#name-input")
                input.clear()
                input.sendKeys("Test User", Keys.TAB)
                
                // Use utils for element interactions
                utils.safeClick(utils.findElement("#submit-btn"))
                
                // Wait for dynamic content
                utils.waitForElementText("#dynamic-content", "Hello Test User", 10)
                
                // Use cache from binding (assuming it's available)
                def cachedData = cache.get("test-key") ?: "default"
                cache.put("test-key", "cached value")
                
                // Use log from binding
                log.debug("Script executed with params: {}", params)
                
                // Take screenshot and get page source
                def screenshot = utils.takeScreenshot()
                def pageSource = utils.getPageSource()
                
                // Scroll and highlight
                utils.scrollToElement(utils.findElement("#test-content"))
                utils.highlightElement(utils.findElement("#test-link"))
                
                // Return comprehensive results
                return [
                    pageTitle: driver.getTitle(),
                    inputValue: input.getAttribute("value"),
                    dynamicContent: utils.findElement("#dynamic-content").getText(),
                    cachedData: cachedData,
                    screenshotTaken: screenshot != null,
                    pageSourceLength: pageSource.length()
                ]
                """;

        String scriptJson = String.format("""
                {
                    "name": "Comprehensive Binding Test Script",
                    "text": "%s",
                    "timeout": 30000
                }
                """, comprehensiveScript.replace("\n", "\\n").replace("\"", "\\\""));

        // Create script
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts"))
                .POST(HttpRequest.BodyPublishers.ofString(scriptJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());

        String scriptId = extractIdFromResponse(createResponse.body());

        // Execute script with parameters
        String executeJson = """
                {
                    "params": {
                        "url": "http://localhost:8911/test-page.html",
                        "waitTime": 10
                    }
                }
                """;

        HttpRequest executeRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/" + scriptId + "/execute"))
                .POST(HttpRequest.BodyPublishers.ofString(executeJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> executeResponse = client.send(executeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, executeResponse.statusCode());

        // Verify comprehensive results
        String resultBody = executeResponse.body();
        assertTrue(resultBody.contains("Test Page"));
        assertTrue(resultBody.contains("Test User"));
        assertTrue(resultBody.contains("Hello Test User"));
        assertTrue(resultBody.contains("true")); // screenshotTaken
    }

    @Test
    void testScriptWithParameters() throws Exception {
        // Create a script that uses parameters
        String paramScript = """
                return [
                    message: params.message ?: "default message",
                    number: params.number ?: 0,
                    flag: params.flag ?: false
                ]
                """;

        String scriptJson = String.format("""
                {
                    "name": "Parameter Test Script",
                    "text": "%s",
                    "timeout": 30000
                }
                """, paramScript.replace("\n", "\\n").replace("\"", "\\\""));

        // Create script
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts"))
                .POST(HttpRequest.BodyPublishers.ofString(scriptJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());

        String scriptId = extractIdFromResponse(createResponse.body());

        // Execute with parameters
        String executeJson = """
                {
                    "params": {
                        "message": "Hello from test",
                        "number": 42,
                        "flag": true
                    }
                }
                """;

        HttpRequest executeRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/" + scriptId + "/execute"))
                .POST(HttpRequest.BodyPublishers.ofString(executeJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> executeResponse = client.send(executeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, executeResponse.statusCode());

        // Verify parameter handling
        String resultBody = executeResponse.body();
        assertTrue(resultBody.contains("Hello from test"));
        assertTrue(resultBody.contains("42"));
        assertTrue(resultBody.contains("true"));
    }

    @Test
    void testScriptNotFound() throws Exception {
        String executeJson = """
                {
                    "params": {}
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/nonexistent-id/execute"))
                .POST(HttpRequest.BodyPublishers.ofString(executeJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void testInvalidScriptValidation() throws Exception {
        String validationJson = """
                {
                    "code": "invalid groovy {{{ code"
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8911/api/scripts/validate"))
                .POST(HttpRequest.BodyPublishers.ofString(validationJson))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Should contain validation errors
        assertTrue(response.body().contains("\"errors\":"));
        assertFalse(response.body().contains("\"errors\":[]"));
    }

    private String extractIdFromResponse(String responseBody) {
        // Simple JSON parsing to extract id - in real implementation you'd use a JSON library
        int idStart = responseBody.indexOf("\"id\":\"") + 6;
        int idEnd = responseBody.indexOf("\"", idStart);
        return responseBody.substring(idStart, idEnd);
    }

    // Test application class for integration testing
    public static class TestApp extends Jooby {
        {
            // Basic Jackson and other modules
            install(new io.jooby.jackson.JacksonModule());
            install(new io.jooby.OpenAPIModule());

            // Simple in-memory services for testing
            getServices().putIfAbsent(machinum.repository.ScriptRepository.class,
                    machinum.repository.JsonFileScriptRepository.create(
                            machinum.repository.ScriptCodeEncoder.create("test-key"),
                            "./build/test-scripts.json"
                    ).init());

            getServices().putIfAbsent(machinum.service.ContainerManager.class,
                    new machinum.service.LocalContainerManager(
                            null, // cache mediator
                            new machinum.service.InMemoryResultStorage(),
                            "./build/test-recordings",
                            "./build/test-html-reports",
                            false // video recording
                    ));

            // MVC controllers
            var scriptController = ScriptController.scriptController(this);
            mvc(scriptController);

            // Serve static test files
            assets("/?*", Paths.get("src/test/resources/static"));
        }
    }
}
