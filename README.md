# Machinum Automata

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-8.0-green.svg)](https://gradle.org/)

Machinum Automata is a powerful, programmable browser automation microservice that enables scalable web automation
through containerized Chrome instances. It executes dynamic Groovy scripts via Selenium WebDriver, providing a robust
platform for simulating thousands of user flows and complex browser interactions.

## 🚀 Features

- **Containerized Chrome Automation**: Runs isolated Chrome instances in Docker containers
- **Dynamic Script Execution**: Execute Groovy scripts with full Selenium WebDriver support
- **Scalable Architecture**: Handle thousands of concurrent browser sessions
- **RESTful API**: Complete HTTP API for script management and execution
- **Web-based UI**: Intuitive web interface for script creation and monitoring
- **Video Recording**: Optional video capture of browser sessions
- **Local & Remote Modes**: Flexible deployment options
- **Real-time Monitoring**: Live execution tracking and result storage

## 🏗️ Architecture

The project follows a modular architecture with three main components:

- **app**: Main web application with REST API and web UI
- **engine**: Core automation engine handling script execution and browser management
- **models**: Shared data models and interfaces

### Technology Stack

- **Backend**: Java 21, Jooby Framework, Gradle
- **Automation**: Selenium WebDriver, Groovy scripting
- **Containerization**: Docker, Chrome containers
- **Frontend**: HTML5, CSS3, JavaScript, Thymeleaf templates
- **Database**: JSON file-based storage (configurable)
- **Testing**: JUnit 5, Testcontainers

## 📋 Prerequisites

- Java 21 or higher
- Docker and Docker Compose
- Gradle 8.0+ (or use included Gradle wrapper)

## 🛠️ Installation

### Option 1: Using Gradle (Development)

```bash
# Clone the repository
git clone https://github.com/gleb619/machinum-automata.git
cd machinum-automata

# Build the project
./gradlew build

# Run the application
./gradlew :app:joobyRun
```

The application will be available at `http://localhost:8076`

### Option 2: Using Docker

```bash
# Build Docker image
docker build -t machinum-automata .

# Run the container
docker run -p 8076:8076 \
  -v $(pwd)/scripts:/machinum-automata/scripts \
  -v $(pwd)/build:/machinum-automata/build \
  machinum-automata
```

### Option 3: Using Installation Script

```bash
# Run the installation script
./install.sh
```

## ⚙️ Configuration

The application uses HOCON configuration files. Default configuration is in `conf/application.conf`.

### Key Configuration Parameters

| Parameter                     | Description                    | Default              |
|-------------------------------|--------------------------------|----------------------|
| `server.port`                 | Server port                    | 8076                 |
| `app.work-mode`               | Work mode: `local` or `remote` | local                |
| `app.scripts-path`            | Path to scripts database       | ./scripts/db.json    |
| `app.html-reports`            | HTML reports directory         | ./build/html-reports |
| `app.cache-directory`         | Cache directory                | ./build/cache        |
| `app.video-recording-enabled` | Enable video recording         | false                |

### Environment Variables

Override configuration using environment variables:

```bash
export SERVER_PORT=8080
export APP_WORK_MODE=remote
export APP_REMOTE_ADDRESS=http://remote-server:8076
export APP_VIDEO_RECORDING_ENABLED=true
```

## 📖 Usage

### Web Interface

Access the web UI at `http://localhost:8076` to:

- Create and edit automation scripts
- Execute scripts manually
- View execution results and reports
- Monitor active sessions
- Configure UI elements

### REST API

#### Execute Script

```bash
curl -X POST http://localhost:8076/api/scripts/execute \
  -H "Content-Type: application/json" \
  -d '{
    "scriptId": "your-script-id",
    "parameters": {}
  }'
```

#### Create Script

```bash
curl -X POST http://localhost:8076/api/scripts \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Automation Script",
    "text": "driver.get(\"https://example.com\")",
    "timeout": 30000
  }'
```

#### Get Execution Results

```bash
curl http://localhost:8076/api/results/{execution-id}
```

### Script Examples

#### Basic Navigation

```groovy
// Navigate to a website
driver.get("https://example.com")

// Wait for element and click
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10))
WebElement button = wait.until(ExpectedConditions.elementToBeClickable(By.id("submit-button")))
button.click()

// Take screenshot
TakesScreenshot screenshot = (TakesScreenshot) driver
byte[] imageBytes = screenshot.getScreenshotAs(OutputType.BYTES)
```

#### Form Automation

```groovy
// Fill out a form
driver.findElement(By.name("username")).sendKeys("testuser")
driver.findElement(By.name("password")).sendKeys("password123")
driver.findElement(By.cssSelector("button[type='submit']")).click()

// Wait for redirect
wait.until(ExpectedConditions.urlContains("/dashboard"))
```

## 📊 Monitoring and Results

### Execution Results

- Real-time execution tracking
- Detailed logs and screenshots
- Performance metrics
- Error reporting and debugging

### HTML Reports

Generated reports include:

- Execution timeline
- Screenshots at key points
- Performance statistics
- Error details and stack traces

## 🔧 Development

### Project Structure

```
machinum-automata/
├── app/                    # Main web application
│   ├── src/main/java/      # Java source files
│   ├── src/main/resources/ # Resources and templates
│   └── build.gradle        # App-specific dependencies
├── engine/                 # Automation engine
│   ├── src/main/groovy/    # Groovy automation scripts
│   └── src/main/java/      # Java engine components
├── models/                 # Shared models and interfaces
├── conf/                   # Configuration files
├── scripts/                # Script storage
└── build.gradle            # Root build file
```

### Building from Source

```bash
# Clean and build
./gradlew clean build

# Run tests
./gradlew test

# Create shadow JAR
./gradlew shadowJar
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

## 🚀 Deployment

### Docker Compose

```yaml
version: '3.8'
services:
  machinum-automata:
    build: .
    ports:
      - "8076:8076"
    volumes:
      - ./scripts:/machinum-automata/scripts
      - ./build:/machinum-automata/build
    environment:
      - APP_WORK_MODE=local
      - APP_VIDEO_RECORDING_ENABLED=true
```

### Kubernetes

Sample deployment manifests are available in the `k8s/` directory (if present).

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Java coding standards
- Write comprehensive tests
- Update documentation
- Ensure backward compatibility

## 📝 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Selenium WebDriver](https://www.selenium.dev/) for browser automation
- [Jooby Framework](https://jooby.io/) for the web framework
- [Docker](https://www.docker.com/) for containerization
- [Chrome](https://www.google.com/chrome/) for the browser engine

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/gleb619/machinum-automata/issues)
- **Discussions**: [GitHub Discussions](https://github.com/gleb619/machinum-automata/discussions)
- **Documentation**: [Wiki](https://github.com/gleb619/machinum-automata/wiki)

---

**Made with ❤️ for scalable browser automation**
