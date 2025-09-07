export function editorApp() {
  return {
    activeEditorTab: 'script',
    script: {},
    scriptBackup: {},
    executionResults: [],
    serverResults: [],
    // Templates
    templates: TEMPLATES,

    // UI Driven stuff
    parsedUIConfig: [],
    uiData: {},
    uiConfigValid: false,
    uiConfigError: '',

    // Script Management
    scriptName: '',
    scripts: [],
    searchQuery: '',
    isUpdating: false,
    scriptInitialized: false,

    headerMode: 'list',

    initEditor() {
        this.resetScript();
        //this.loadValue('script', this.script);
        //this.loadValue('scriptId', undefined);
        this.loadValue('activeEditorTab', this.activeEditorTab);
//        this.script.uiConfig = JSON.stringify(this.receiveValue('script', undefined), null, 2);
        this.fetchScriptsOnStart();
    },

    resetScript() {
        this.script = {
          id: '',
          name: '',
          text: '',
          timeout: 60,
          uiConfig: ''
        };
    },

    async fetchScriptsOnStart() {
        await this.fetchScripts();
        if(this.scripts.length > 0) {
            const scriptId = this.loadValue('scriptId', undefined);
            if(scriptId) {
                const script = this.scripts.find(item => item.id === scriptId);
                this.setScript(script);
            } else {
                this.setScript(this.scripts[0]);
            }

            this.validateUIConfig();
            this.tryParseUIConfig();
            this.scriptInitialized = true;
        } else {
            this.headerMode = 'create';
        }
    },
    
    loadTemplate(templateName) {
        this.script.text = this.templates[templateName];
        this.showToast(`Template "${templateName}" loaded`);
    },

    async validateScriptApi(code, language) {
        try {
            const response = await fetch('/api/scripts/validate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ code })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();

            if (result.errors && Array.isArray(result.errors)) {
                return {
                    errors: result.errors.map(e => ({
                        line: e.line || 0,
                        message: e.message || 'Unknown'
                    }))
                };
            }

            return { errors: [] };
        } catch (error) {
            console.warn('API call error:', error);
            return { errors: [{ line: 0, message: 'Failed to validate script' }] };
        }
    },

    async executeScriptBase(url, body) {
        this.loading.executeScript = true;
        const startTime = Date.now();

        try {
            const response = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            const result = await response.json();
            const duration = Date.now() - startTime;
            const { screenshot, videoFile, htmlFile, ...executionResult } = result;

            this.executionResults.unshift({
                timestamp: new Date(),
                success: result.success,
                data: executionResult,
                screenshot: screenshot || '',
                videoFile: videoFile || '',
                htmlFile: htmlFile || '',
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
            console.error('API call error:', error);
            this.showToast(`Execution error: ${error.message}`, true);
        } finally {
            this.loading.executeScript = false;
        }
    },

    async executeScript() {
        if (!this.selectedSession) return;

        await this.executeScriptBase(`/api/sessions/${this.selectedSession}/execute`, {
          script: this.script.text,
          timeout: this.script.timeout,
          params: this.uiData,
       });
    },

    async executeScriptById() {
        await this.executeScriptBase(`/api/scripts/${this.script.id}/execute`, {
            params: this.uiData,
        });
    },

    get uiResult() {
        return (this.executionResults.length > 0 ? this.executionResults[0] : { data: { data: {} } })?.data?.data;
    },

    // Script Management (API Driven)
    async fetchScripts() {
        try {
            const response = await fetch(`/api/scripts`);
            if (!response.ok) throw new Error('Network response was not ok.');
            this.scripts = await response.json();
        } catch (error) {
            console.error('Failed to load scripts:', error);
            this.showToast('Failed to load scripts.', true);
            this.scripts = [];
        }
    },

    async saveScript(newName) {
        if (!this.scriptName.trim() || !this.script.text.trim()) return;

        this.validateUIConfig();
        if(!this.uiConfigValid) return;


        const { name, uiConfig, ...scriptData } = this.script;
        scriptData.name = newName;
        scriptData.uiConfig = JSON.parse(uiConfig);

        try {
            const response = await fetch(`/api/scripts`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(scriptData),
            });

            if (!response.ok) throw new Error('Failed to save script on the server.');
            
            this.scriptName = '';
            this.showToast('Script saved successfully!');
            const data = await response.json();
            this.setScript(data);
            await this.fetchScripts(); // Refresh list from server
        } catch (error) {
            console.error('Save script error:', error);
            this.showToast('Error saving script.', true);
        }
    },

    async updateScript() {
        this.validateUIConfig();
        const dataChanged = !(this.scriptBackup.id == this.script.id &&
            this.scriptBackup.name == this.script.name &&
            this.scriptBackup.text == this.script.text &&
            this.scriptBackup.timeout == this.script.timeout &&
            this.scriptBackup.uiConfig == this.script.uiConfig);

        if(!dataChanged || !this.uiConfigValid) return;

        if (this.isUpdating) return Promise.reject(new Error('Update is already in progress.'));

        this.isUpdating = true;

        const { uiConfig, ...scriptData } = this.script;
        scriptData.uiConfig = JSON.parse(uiConfig || '[]');

        try {
            const response = await fetch(`/api/scripts/${this.script.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(scriptData),
            });

            if (!response.ok) throw new Error('Failed to update script on the server.');

            const data = await response.json();
            this.setScript(data);
        } catch (error) {
            console.error('Update script error:', error);
            this.showToast('Error updating script.', true);
        } finally {
          setTimeout(() => { this.isUpdating = false; }, 5000);
        }
    },

    async loadScript(scriptId) {
        try {
            const response = await fetch(`/api/scripts/${scriptId}`);
            if (!response.ok) throw new Error('Script not found.');
            
            const scriptToLoad = await response.json();
            this.setScript(scriptToLoad);
            
            this.showToast(`Script "${scriptToLoad.name}" loaded.`);
        } catch (error) {
            console.error('Load script error:', error);
            this.showToast('Error loading script.', true);
        }
    },

    async deleteScript(scriptId) {
        if (!confirm('Are you sure you want to delete this script?')) return;
        
        try {
            const response = await fetch(`/api/scripts/${scriptId}`, {
                method: 'DELETE',
            });

            if (!response.ok) throw new Error('Failed to delete script on the server.');
            
            this.showToast('Script deleted successfully!');
            await this.fetchScripts();
            this.resetScript();
        } catch (error) {
            console.error('Delete script error:', error);
            this.showToast('Error deleting script.', true);
        }
    },

    get filteredScripts() {
        if (!this.searchQuery) {
            return [...this.scripts];
        } else {
            const lowerCaseQuery = this.searchQuery.toLowerCase();
            return this.scripts.filter(script =>
                script.name.toLowerCase().includes(lowerCaseQuery)
            );
        }
    },

    setScript(data) {
        const { uiConfig, ...rest } = data;
        this.script = {...rest};
        this.script.uiConfig = this.jsonStringify(uiConfig);
        this.scriptBackup = JSON.parse(this.jsonStringify(this.script));
    },
    
    validateUIConfig() {
        try {
            const temp = JSON.parse(this.script.uiConfig);
            this.uiConfigValid = Array.isArray(temp);
            if(this.uiConfigValid) {
                this.uiConfigError = '';
            } else {
                this.uiConfigError = 'Object is not an array!';
            }
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
            this.parsedUIConfig = this.script.uiConfig ? JSON.parse(this.script.uiConfig) : [];
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
    clearUIData() {
        this.parsedUIConfig.forEach(element => {
            this.uiData[element.name] = element.defaultValue || '';
        });
        this.$dispatch('ui-element-refresh');
    },

    thisApp() {
        return this;
    },

    resolveScriptText(app) {
        return () => {
            return new Promise((resolve, reject) => {
                setTimeout(() => {
                  resolve(app.script?.text || '');
                }, 100);
            });
        };
    },

    get curlExample() {
        if(!this.selectedSession) return '';
        const request = {
            script: this.script.text,
            timeout: this.script.timeout,
            params: this.uiData
        };
        return `curl -X POST ${window.location.origin}/api/sessions/${this.selectedSession}/execute \\
  -H 'Content-Type: application/json' \\
  -d '${JSON.stringify(request)}'`;
    },

    get curlExampleById() {
        if(!this.script?.id) return '';
        const request = {
            params: this.uiData
        };
        return `curl -X POST ${window.location.origin}/api/scripts/${this.script.id}/execute \\
  -H 'Content-Type: application/json' \\
  -d '${JSON.stringify(request)}'`;
    },

    async fetchServerResults() {
        try {
            const response = await fetch('/api/results');
            if (!response.ok) throw new Error('Failed to fetch server results');
            const results = await response.json();
            this.serverResults = results.map(result => ({
                ...result,
                timestamp: new Date(), // placeholder timestamp
                duration: result.executionTime
            }));
        } catch (error) {
            console.error('Failed to fetch server results:', error);
            this.showToast('Failed to load server results.', true);
        }
    },

    async deleteServerResult(id) {
        if (!confirm('Are you sure you want to delete this result?')) return;
        try {
            const response = await fetch(`/api/results/${id}`, { method: 'DELETE' });
            if (!response.ok) throw new Error('Failed to delete result');
            this.showToast('Result deleted successfully!');
            await this.fetchServerResults(); // refresh
        } catch (error) {
            console.error('Failed to delete result:', error);
            this.showToast('Error deleting result.', true);
        }
    },

  }
};

//TODO move to editor.js
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
