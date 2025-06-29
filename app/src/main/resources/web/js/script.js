// app.js
const startTime = new Date().getTime();

import { initApp } from './init.js';
import { utilsApp } from './core.js';
import { automationApp } from './automation.js';
import { editorApp } from './editor.js';
import { uiConfigApp } from './ui-config.js';
import { initUiElementDirective } from './directive/ui-element-directive.js'
import { initCodeEditorDirective } from './directive/code-editor-directive.js'

/**
 * Creates the main application with combined functionality
 * from list and edit modules
 */
export function app() {
    let combinedApp = {};
    // Combine the list and edit functions using Object.assign
    try {
        combinedApp = Object.defineProperties({},
            {
                ...Object.getOwnPropertyDescriptors(initApp()),
                ...Object.getOwnPropertyDescriptors(utilsApp()),
                ...Object.getOwnPropertyDescriptors(automationApp()),
                ...Object.getOwnPropertyDescriptors(editorApp()),
                ...Object.getOwnPropertyDescriptors(uiConfigApp()),
            }
        );
    } catch(e) {
        debugger;
        console.error("error: ", e);
    }

    return combinedApp;
}

document.addEventListener("DOMContentLoaded", function(event) {
    const currentTime = new Date().getTime();
    const elapsedTime = currentTime - startTime;
    const remainingTime = Math.max(10, 500 - elapsedTime);

    initUiElementDirective();
    initCodeEditorDirective();
    Alpine.data('app', app);
    Alpine.start();

    showLoader(remainingTime);
});

function showLoader(remainingTime) {
  const splashScreen = document.getElementsByClassName('splash-screen')[0];
  const mainContent = document.getElementsByClassName('main-content')[0];
  splashScreen.style.display = 'flex';
  splashScreen.style.opacity = '1';
  mainContent.style.opacity = '0';

  setTimeout(() => {
      splashScreen.style.opacity = '0';
      mainContent.style.opacity = '1';

      setTimeout(() => {
          splashScreen.style.display = 'none';
      }, 250);
  }, remainingTime);
}