# Machinum Automata Agent Instructions

This document provides instructions for an AI agent to interact with, analyze, and manage the `Machinum Automata`
project.

## 1. Project Overview

**Machinum Automata** is a programmable browser automation microservice. Its primary function is to execute Groovy
scripts using Selenium WebDriver within containerized Chrome instances. It's designed for scalable web automation and
can simulate thousands of user flows.

**Core functionalities:**

- Execute dynamic Groovy scripts for browser automation.
- Manage automation tasks via a RESTful API and a web UI.
- Run browser sessions in isolated Docker containers.
- Record videos of automation sessions.
- Store execution results and generate HTML reports.

## 2. Technology Stack

The project is built on the following technologies. Understanding them is crucial for analysis and modification.

- **Backend:** Java 21, Jooby Framework
- **Build Tool:** Gradle 8.0+
- **Automation Engine:** Selenium WebDriver
- **Scripting:** Groovy
- **Containerization:** Docker
- **Frontend:** Thymeleaf, JavaScript, HTML5, CSS3
- **Configuration:** HOCON (`.conf` files)
- **Testing:** JUnit 5, Testcontainers

## 3. Project Structure

The project has a modular architecture. Your actions should target the correct module.

```

.
├── app/                  \# Main web application module (REST API, Web UI)
│   ├── src/main/java/    \# Java source code for the web app
│   └── src/main/resources/ \# Templates, static assets, app config
├── engine/               \# Core automation engine module
│   ├── src/main/groovy/  \# Example or internal Groovy scripts
│   └── src/main/java/    \# Java source code for the automation engine
├── models/               \# Shared data models and interfaces
│   └── src/main/java/
├── conf/                 \# HOCON configuration files (application.conf)
├── scripts/              \# Default storage for user scripts (db.json)
├── build/                \# Build output directory (reports, cache, JARs)
├── build.gradle          \# Root Gradle build script
├── settings.gradle       \# Gradle module definitions
└── Dockerfile            \# Docker build instructions

````

- **`app`**: Focus here for changes to the API endpoints, web interface, or application startup logic.
- **`engine`**: This is the core logic. Analyze or modify this module for changes related to script execution, browser
  management, and Selenium interaction.
- **`models`**: Contains plain Java objects (POJOs) used across modules.

## 4. Key Commands

Use the Gradle wrapper (`./gradlew`) for all build-related tasks.

### Building the Project

To compile all modules and run checks:

```bash
./gradlew build
````

### Running the Application (Development)

To start the application locally using the Jooby Gradle plugin:

```bash
./gradlew :app:joobyRun
```

The application will be available at `http://localhost:8076`.

### Running Tests

To execute the test suite for all modules:

```bash
./gradlew test
```

Test reports are generated in `build/reports/tests/`.

### Building a Distributable JAR

To create a "fat JAR" (uber JAR) containing all dependencies:

```bash
./gradlew shadowJar
```

The JAR will be located in `build/libs/`.

### Running with Docker

1. **Build the Docker image:**
   ```bash
   docker build -t machinum-automata .
   ```
2. **Run the container:**
   ```bash
   docker run -p 8076:8076 \
     -v $(pwd)/scripts:/machinum-automata/scripts \
     -v $(pwd)/build:/machinum-automata/build \
     machinum-automata
   ```

## 5. Configuration

- The primary configuration file is `conf/application.conf`. It uses the HOCON format.
- **Key Parameters:**
    - `server.port`: The port the application runs on (Default: `8076`).
    - `app.work-mode`: `local` or `remote`. Determines how Chrome instances are managed.
    - `app.scripts-path`: Path to the JSON file storing user scripts.
    - `app.video-recording-enabled`: `true` or `false` to enable/disable session recording.
- Configuration can be overridden with environment variables. The format is snake\_case and uppercase (e.g.,
  `server.port` becomes `SERVER_PORT`).

## 6. Automation Scripts

- Scripts are written in **Groovy** and use the **Selenium WebDriver API**.
- The `driver` object (an instance of `WebDriver`) is pre-injected and available in the script's scope.
- Scripts are stored in the JSON file defined by `app.scripts-path` (default: `scripts/db.json`) and managed via the API
  or UI.
- When tasked with writing or analyzing a script, assume full access to the Selenium API.

**Example Groovy Script:**

```groovy
// The 'driver' variable is automatically available.
driver.get("[https://www.google.com](https://www.google.com)")

// Use WebDriverWait for robust scripts
def wait = new WebDriverWait(driver, Duration.ofSeconds(10))
def searchBox = wait.until(ExpectedConditions.elementToBeClickable(By.name("q")))

searchBox.sendKeys("Scalable Browser Automation")
searchBox.submit()
```

## 7. Agent Task Guidelines

- **Analyze Code:** When asked to analyze functionality, start by identifying the relevant module (`app`, `engine`).
  Navigate to the `src/main/java` directory within that module.
- **Modify Configuration:** To change application behavior, modify `conf/application.conf` or suggest exporting the
  corresponding environment variables.
- **Run the Application:** Use the Gradle or Docker commands specified in section 4.
- **Troubleshoot Build Failures:** Check the console output from the `./gradlew build` command. The error will typically
  indicate a compilation problem or a failing test.
- **Interact with the API:** Use `curl` commands as shown in the `README.md` to interact with the running application
  for testing or management. The base URL is `http://localhost:8076`.

```bash
# Example: Execute a script via API
curl -X POST http://localhost:8076/api/scripts/execute \
 -H "Content-Type: application/json" \
 -d '{"scriptId": "your-script-id", "parameters": {}}'
```