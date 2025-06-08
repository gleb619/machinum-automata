export function automationApp() {
  return {

    config: {
        version: 'latest',
        arguments: [
            '--window-size=1920,1080',
            '--lang=en,en-US',
            '--accept-lang=ru-RU',
            '--accept-language=ru-RU',
            '--disable-translate'
        ],
        headless: true,
        timeoutSeconds: 30,
        userAgent: '',
        implicitWaitSeconds: 10,
        pageLoadTimeoutSeconds: 30,
        scriptTimeoutSeconds: 30,
        recordingMode: 'RECORD_ALL',
        recordingDirectory: './build/recordings',
        environmentVariables: [
            {key: 'LANG', value: 'ru_RU'},
            {key: 'LANGUAGE', value: 'ru_RU'}
        ],
        experimentalOptions: [
            {key: 'prefs', value: '{"intl.accept_languages":"ru,ru_RU","intl.selected_languages":"ru,ru_RU"}'}
        ],
        acceptInsecureCerts: true,
        pageLoadStrategy: 'EAGER'
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

    // Templates
    templates: TEMPLATES,

    // Initialization
    init() {
        this.fetchHealth();
        this.fetchSessions();
    },

    // API Methods
    async createSession() {
        this.loading.createSession = true;
        try {
            // Convert key-value arrays to maps for backend
            const configToSend = {
                ...this.config,
                environmentVariables: this.config.environmentVariables.reduce((acc, env) => {
                    if (env.key) acc[env.key] = env.value;
                    return acc;
                }, {}),
                experimentalOptions: this.config.experimentalOptions.reduce((acc, opt) => {
                    if (opt.key) {
                        try {
                            acc[opt.key] = JSON.parse(opt.value);
                        } catch {
                            acc[opt.key] = opt.value;
                        }
                    }
                    return acc;
                }, {})
            };

            const response = await fetch('/api/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(configToSend)
            });

            if (response.ok) {
                const result = await response.json();
                this.showToast('Session created successfully!');
                this.fetchSessions();
                this.fetchHealth();
                this.selectedSession = result.id;
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.showToast(`Failed to create session: ${error.message}`, true);
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
            this.showToast(`Failed to fetch sessions: ${error.message}`, true);
        }
    },

    async deleteSession(sessionId) {
        try {
            const response = await fetch(`/api/sessions/${sessionId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                this.showToast('Session deleted successfully!');
                if (this.selectedSession === sessionId) {
                    this.selectedSession = null;
                }
                this.fetchSessions();
                this.fetchHealth();
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.showToast(`Failed to delete session: ${error.message}`, true);
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
                this.showToast('Script executed successfully!');
            } else {
                this.showToast('Script execution failed!', true);
            }
        } catch (error) {
            this.executionResults.push({
                timestamp: new Date(),
                success: false,
                data: { error: error.message },
                duration: Date.now() - startTime
            });
            this.showToast(`Execution error: ${error.message}`, true);
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
        this.showToast(`Session ${sessionId} selected`);
    },

    addArgument() {
        this.config.arguments.push('');
    },

    removeArgument(index) {
        this.config.arguments.splice(index, 1);
    },

    addEnvironmentVariable() {
        this.config.environmentVariables.push({key: '', value: ''});
    },

    removeEnvironmentVariable(index) {
        this.config.environmentVariables.splice(index, 1);
    },

    addExperimentalOption() {
        this.config.experimentalOptions.push({key: '', value: ''});
    },

    removeExperimentalOption(index) {
        this.config.experimentalOptions.splice(index, 1);
    },

    loadTemplate(templateName) {
        this.script.code = this.templates[templateName];
        this.showToast(`Template "${templateName}" loaded`);
    },

  };
}

const TEMPLATES = {

    navigation: `driver.get("https://httpbin.org")
utils.waitForElement("h1")
return [title: driver.getTitle(), url: driver.getCurrentUrl()]`,

    form: `driver.get("https://httpbin.org/forms/post")
driver.findElement(By.name("custname")).sendKeys("Test User")
utils.randomSleep(500, 1000)
driver.findElement(By.cssSelector("input[type='submit']")).click()
return [success: true, screenshot: utils.takeScreenshot()]`,


    complex: `try {
driver.get("https://httpbin.org")

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


    screenshot: `driver.get("https://httpbin.org")
utils.waitForPageLoad()
def screenshot = utils.takeScreenshot()
return [
title: driver.getTitle(),
url: driver.getCurrentUrl(),
screenshot: screenshot,
timestamp: new Date().toString()
]`

};