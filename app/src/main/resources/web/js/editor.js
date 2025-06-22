export function editorApp() {
  return {
    activeEditorTab: 'script',
    script: {
      code: '',
      timeout: 60
    },
    executionResults: [],

    // UI Driven stuff
    uiConfig: '',
    parsedUIConfig: [],
    uiData: {},
    uiConfigValid: false,
    uiConfigError: '',

    // Script Management
    scriptName: '',
    selectedSavedScript: '',
    savedScripts: [],
    searchQuery: '',
    filteredScripts: [],

    initEditor() {
        this.loadValue('script', this.script);
        this.loadValue('activeEditorTab', this.activeEditorTab);
        this.uiConfig = JSON.stringify(this.receiveValue('uiConfig', undefined), null, 2);
        this.validateUIConfig();
        this.tryParseUIConfig();
    },

    loadTemplate(templateName) {
        this.script.code = this.templates[templateName];
        this.showToast(`Template "${templateName}" loaded`);
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
                    timeout: this.script.timeout,
                    params: this.uiData,
                })
            });

            const result = await response.json();
            const duration = Date.now() - startTime;
            const { screenshot, videoFile, ...executionResult } = result;

            this.executionResults.unshift({
                timestamp: new Date(),
                success: result.success,
                data: executionResult,
                screenshot: screenshot || '',
                videoFile: videoFile || '',
                executionTime: result.executionTime,
                duration: duration / 1000
            });

            this.$dispatch('ui-element-refresh');

            if (response.ok && result.success) {
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

    get uiResult() {
        //return (this.executionResults.length > 0 ? this.executionResults[0] : { data: '' })?.data;
        return {
            "translated_text": "\"У нас еще есть храбрые греки! Они сражались бок о бок с нами!\" Кир Младший посмотрел направо, где греческая тяжелая пехота начала свой медленный строй. Увидев это, он почувствовал уверенность: \"Нас не остановить! Мы непобедимы!!!\"\n«За победу!» - первым крикнул Артапатус, затем все закричали друг за другом: «За победу!!!»\nПодняв боевой дух, Сайрус Младший поднял правую руку: \"Друзья! Воины! После этой победы я клянусь Маздой, верховным богом, что сделаю все возможное, чтобы отплатить вам за вашу дружбу и преданность!!!\""
        };
    },

    // Storage Management
    loadFromStorage() {
        const stored = localStorage.getItem('groovyWorkplace');
        if (stored) {
            const data = JSON.parse(stored);
            this.script = data.script || this.script;
            this.uiConfig = data.uiConfig || '';
            this.selectedSession = data.selectedSession || '';
            this.parseUIConfig();
        }
    },

    saveToStorage() {
        localStorage.setItem('groovyWorkplace', JSON.stringify({
            script: this.script,
            uiConfig: this.uiConfig,
            selectedSession: this.selectedSession
        }));
    },

    // Session Management
    async refreshSessions() {
        try {
            const response = await fetch('/api/sessions');
            this.sessions = await response.json();
        } catch (error) {
            this.showToast('Failed to load sessions', true);
        }
    },

    // Script Management
    async loadSavedScripts() {
        try {
            const stored = localStorage.getItem('savedScripts');
            this.savedScripts = stored ? JSON.parse(stored) : [];
            this.filteredScripts = [...this.savedScripts];
        } catch (error) {
            this.savedScripts = [];
            this.filteredScripts = [];
        }
    },

    async saveScript() {
        if (!this.scriptName || !this.script.code) return;

        const scriptData = {
            id: Date.now().toString(),
            name: this.scriptName,
            code: this.script.code,
            timeout: this.script.timeout,
            uiConfig: this.uiConfig,
            createdAt: new Date().toISOString()
        };

        this.savedScripts.push(scriptData);
        localStorage.setItem('savedScripts', JSON.stringify(this.savedScripts));
        this.filteredScripts = [...this.savedScripts];
        this.scriptName = '';
        this.showToast('Script saved successfully!');
    },

    async loadScript() {
        const script = this.savedScripts.find(s => s.id === this.selectedSavedScript);
        if (script) {
            this.script.code = script.code;
            this.script.timeout = script.timeout;
            this.uiConfig = script.uiConfig || '';
            this.parseUIConfig();
            this.saveToStorage();
            this.showToast('Script loaded successfully!');
        }
    },

    async deleteScript() {
        this.savedScripts = this.savedScripts.filter(s => s.id !== this.selectedSavedScript);
        localStorage.setItem('savedScripts', JSON.stringify(this.savedScripts));
        this.filteredScripts = [...this.savedScripts];
        this.selectedSavedScript = '';
        this.showToast('Script deleted successfully!');
    },

    filterScripts() {
        if (!this.searchQuery) {
            this.filteredScripts = [...this.savedScripts];
        } else {
            this.filteredScripts = this.savedScripts.filter(script =>
                script.name.toLowerCase().includes(this.searchQuery.toLowerCase())
            );
        }
    },

    validateUIConfig() {
        try {
            JSON.parse(this.uiConfig);
            this.uiConfigValid = true;
            this.uiConfigError = '';
        } catch (error) {
            this.uiConfigValid = false;
            this.uiConfigError = error.message;
        }
    },

    previewUI() {
        this.parseUIConfig();
        if (this.parsedUIConfig.length > 0) {
            this.activeEditorTab = 'ui-execute';
            this.showToast('UI preview ready!');
        }
    },

    tryParseUIConfig() {
        if(this.uiConfigValid) {
            this.parseUIConfig();
        }
    },

    parseUIConfig() {
        try {
            this.parsedUIConfig = this.uiConfig ? JSON.parse(this.uiConfig) : [];
            this.uiConfigValid = true;
            this.uiConfigError = '';

            // Initialize UI data for new elements
            this.parsedUIConfig.forEach(element => {
                if (!(element.name in this.uiData) && element.source === 'uiData') {
                    this.uiData[element.name] = element.defaultValue || '';
                }
            });
        } catch (error) {
            this.parsedUIConfig = [];
            this.uiConfigValid = false;
            this.uiConfigError = error.message;
        }
    },

    // UI Data Management
    bindResultData(resultData) {
        Object.keys(resultData).forEach(key => {
            if (key in this.uiData) {
                this.uiData[key] = resultData[key];
            }
        });
        this.showToast('Result data bound to UI!');
    },

    clearUIData() {
        this.parsedUIConfig.forEach(element => {
            this.uiData[element.name] = element.defaultValue || '';
        });
        this.showToast('UI data cleared!');
    },

    get curlExample() {
        if(!this.selectedSession) return '';
        const request = {
            script: this.script.code,
            timeout: this.script.timeout,
            params: this.uiData
        };
        return formatCurlCommand(`curl -X POST ${window.location.origin}/api/sessions/${this.selectedSession}/execute -H 'Content-Type: application/json' -d '${JSON.stringify(request, null, 2)}'`);
    }

  }
};

function formatCurlCommand(rawCurl) {
  if (!rawCurl || typeof rawCurl !== 'string') {
    return '';
  }

  const parts = rawCurl.match(/(?:[^\s"']+|"[^"]*"|'[^']*')+/g) || [];

  if (parts.length < 2) {
    return rawCurl;
  }

  const lines = [parts[0]];
  let lastPartWasAnOption = false;

  for (let i = 1; i < parts.length; i++) {
    const part = parts[i];
    const isOption = part.startsWith('-');

    if (isOption) {
      lines.push(` \\\n  ${part}`);
      lastPartWasAnOption = true;
    } else {
      if (lastPartWasAnOption) {
        lines[lines.length - 1] += ` ${part}`;
      } else {
        lines[0] += ` ${part}`;
      }
      lastPartWasAnOption = false;
    }
  }

  return lines.join('');
}
