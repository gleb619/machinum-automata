export function appApp() {
  return {

    config: {
        version: 'latest',
        arguments: ['--window-size=1920,1080'],
        headless: true,
        timeoutSeconds: 30,
        userAgent: '',
        implicitWaitSeconds: 10,
        pageLoadTimeoutSeconds: 30,
        scriptTimeoutSeconds: 30
    },
    script: {
        code: '',
        timeout: 60
    },
    sessions: [],
    selectedSession: null,
    executionResults: [],
    health: {
        activeSessions: 0
    },
    loading: {
        createSession: false,
        executeScript: false
    },
    notification: {
        show: false,
        message: '',
        type: 'info'
    },

    // Templates
    templates: {
        navigation: `driver.get("https://example.com")
utils.waitForElement("h1")
return [title: driver.getTitle(), url: driver.getCurrentUrl()]`,

        form: `driver.get("https://httpbin.org/forms/post")
driver.findElement(By.name("custname")).sendKeys("Test User")
utils.randomSleep(500, 1000)
driver.findElement(By.cssSelector("input[type='submit']")).click()
return [success: true, screenshot: utils.takeScreenshot()]`,

        complex: `try {
driver.get("https://example.com")

if (utils.isElementPresent("#login-form")) {
def usernameField = driver.findElement(By.id("username"))
usernameField.sendKeys("testuser")

utils.randomSleep(200, 500)

def submitButton = driver.findElement(By.cssSelector("button[type='submit']"))
utils.scrollToElement(submitButton)
submitButton.click()

utils.waitForElement(".success-message", 10)
return [status: "login_success", screenshot: utils.takeScreenshot()]
} else {
return [status: "no_login_form"]
}
} catch (Exception e) {
return [status: "error", message: e.message, screenshot: utils.takeScreenshot()]
}`,

        screenshot: `driver.get("https://example.com")
utils.waitForPageLoad()
def screenshot = utils.takeScreenshot()
return [
title: driver.getTitle(),
url: driver.getCurrentUrl(),
screenshot: screenshot,
timestamp: new Date().toString()
]`
    },

    // Initialization
    init() {
        this.fetchHealth();
        this.fetchSessions();
    },

    // API Methods
    async createSession() {
        this.loading.createSession = true;
        try {
            const response = await fetch('/api/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(this.config)
            });

            if (response.ok) {
                const result = await response.json();
                this.showNotification('Session created successfully!', 'success');
                this.fetchSessions();
                this.fetchHealth();
                this.selectedSession = result.id;
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.showNotification(`Failed to create session: ${error.message}`, 'error');
        } finally {
            this.loading.createSession = false;
        }
    },

    async fetchSessions() {
        try {
            const response = await fetch('/api/sessions');
            if (response.ok) {
                this.sessions = await response.json();
            }
        } catch (error) {
            this.showNotification(`Failed to fetch sessions: ${error.message}`, 'error');
        }
    },

    async deleteSession(sessionId) {
        try {
            const response = await fetch(`/api/sessions/${sessionId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.showNotification('Session deleted successfully!', 'success');
                if (this.selectedSession === sessionId) {
                    this.selectedSession = null;
                }
                this.fetchSessions();
                this.fetchHealth();
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.showNotification(`Failed to delete session: ${error.message}`, 'error');
        }
    },

    async executeScript() {
        if (!this.selectedSession) return;

        this.loading.executeScript = true;
        const startTime = Date.now();

        try {
            const response = await fetch(`/api/sessions/${this.selectedSession}/execute`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    script: this.script.code,
                    timeout: this.script.timeout
                })
            });

            const result = await response.json();
            const duration = Date.now() - startTime;

            this.executionResults.push({
                timestamp: new Date(),
                success: response.ok,
                data: result,
                duration: duration
            });

            if (response.ok) {
                this.showNotification('Script executed successfully!', 'success');
            } else {
                this.showNotification('Script execution failed!', 'error');
            }
        } catch (error) {
            this.executionResults.push({
                timestamp: new Date(),
                success: false,
                data: { error: error.message },
                duration: Date.now() - startTime
            });
            this.showNotification(`Execution error: ${error.message}`, 'error');
        } finally {
            this.loading.executeScript = false;
        }
    },

    async fetchHealth() {
        try {
            const response = await fetch('/api/health');
            if (response.ok) {
                this.health = await response.json();
            }
        } catch (error) {
            console.error('Failed to fetch health:', error);
        }
    },

    // Helper Methods
    selectSession(sessionId) {
        this.selectedSession = sessionId;
        this.showNotification(`Session ${sessionId} selected`, 'info');
    },

    addArgument() {
        this.config.arguments.push('');
    },

    removeArgument(index) {
        this.config.arguments.splice(index, 1);
    },

    loadTemplate(templateName) {
        this.script.code = this.templates[templateName];
        this.showNotification(`Template "${templateName}" loaded`, 'info');
    },

    showNotification(message, type = 'info') {
        this.notification = { show: true, message, type };
        setTimeout(() => {
            this.notification.show = false;
        }, 4000);
    }

  };
}