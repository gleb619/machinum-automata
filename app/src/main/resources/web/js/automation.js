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
        videoRecordingEnabled: false,
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
            {key: 'prefs', value: '{\n "intl.accept_languages":"ru,ru_RU",\n "intl.selected_languages":"ru,ru_RU" }'}
        ],
        acceptInsecureCerts: true,
        pageLoadStrategy: 'EAGER'
    },
    sessions: [],
    selectedSession: undefined,
    health: {
        activeSessions: 0
    },
    loading: {
        createSession: false,
        executeScript: false
    },


    initAutomation() {
        this.fetchHealth();
        this.fetchSessions();
        this.loadValue('config', this.config);
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
                this.backupValue('config', this.config);
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
                if(!this.selectedSession && this.sessions.length > 0) {
                    this.selectSession(this.sessions[0].id);
                }
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

  };
}
