export function initCodeEditorDirective() {
const debounceContext = {};
Alpine.directive('code-editor', (el, { expression }, { evaluate, cleanup, effect }) => {
    if (el._x_code_editor_initialized) return;
    el._x_code_editor_initialized = true;

    let options = {};
    let state = {
        code: '',
        language: '',
        searchTerm: '',
        errors: [],
        inputDebounce: 10,
    };
    let debouncedUpdateHandler;
    let debouncedChangeHandler;
    let debouncedBlurHandler;
    let debouncedValidationHandler;
    let isInitialized = false;

    const template = `
        <div class="font-mono text-sm" data-id="root">
            <div data-id="toolbar" class="flex gap-2 mb-2 items-center">
                <input data-id="search-input" type="text" placeholder="Search..." class="px-3 py-1 border rounded flex-1 max-w-xs">
                <button data-id="format-btn" class="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600">Format</button>
                <button data-id="clear-btn" class="px-3 py-1 bg-red-500 text-white rounded hover:bg-red-600">Clear</button>
            </div>
            <div data-id="editor-container" class="relative flex border rounded-md overflow-hidden bg-white">
                <div data-id="line-numbers" class="bg-gray-100 text-right py-2 px-3 text-gray-500 select-none min-w-[3rem] border-r"></div>
                <div class="relative flex-1">
                    <pre data-id="highlight-container" class="absolute inset-0 overflow-auto p-2 pointer-events-none leading-6 whitespace-pre-wrap"></pre>
                    <textarea data-id="editor" spellcheck="false" class="absolute inset-0 w-full h-full bg-transparent text-transparent caret-black resize-none p-2 overflow-auto leading-6 whitespace-pre-wrap"></textarea>
                </div>
            </div>
            <div data-id="error-display" class="mt-2 p-2 bg-red-100 border border-red-300 rounded text-red-700 hidden"></div>
        </div>`;
    el.innerHTML = template;

    const query = (id) => el.querySelector(`[data-id="${id}"]`);
    const [editor, highlightContainer, lineNumbersEl, errorDisplay, searchInput, formatBtn, clearBtn, toolbar, editorContainer] =
        ['editor', 'highlight-container', 'line-numbers', 'error-display', 'search-input', 'format-btn', 'clear-btn', 'toolbar', 'editor-container'].map(query);

    const render = () => {
        if (!window.Prism || !highlightContainer) return;
        const grammar = Prism.languages[state.language];
        let highlightedCode = grammar ? Prism.highlight(state.code, grammar, state.language) : state.code;
        if (state.searchTerm) {
            const escaped = state.searchTerm.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            highlightedCode = highlightedCode.replace(new RegExp(`(${escaped})`, 'gi'), `<span class="search-match">$1</span>`);
        }
        highlightContainer.innerHTML = highlightedCode + '\n';
    };

    const updateLineNumbers = () => {
        if (!options.showLineNumbers) return;
        const lineCount = Math.max(1, state.code.split('\n').length);
        const errorMap = Object.fromEntries(state.errors.map(e => [e.line, e.message]));

        lineNumbersEl.innerHTML = Array.from({ length: lineCount }, (_, i) => {
          const lineNum = i + 1;
          const message = errorMap[lineNum];
          const className = message ? 'line-error' : 'leading-6';
          const title = message ? ` title="${message.replace(/"/g, '&quot;')}"` : '';
          return `<div class="${className}"${title}>${lineNum}</div>`;
        }).join('');
    };

    const validate = async () => {
        state.errors = [];

        // Local JSON validation for immediate feedback
        if (options.validateJson && state.language === 'json' && state.code.trim()) {
            try { JSON.parse(state.code); } catch (e) {
                const match = e.message.match(/position (\d+)/);
                if (match) {
                    const pos = parseInt(match[1]); let currentPos = 0;
                    const lines = state.code.split('\n');
                    for (let i = 0; i < lines.length; i++) {
                        if (currentPos + lines[i].length >= pos) { state.errors.push(i + 1); break; }
                        currentPos += lines[i].length + 1;
                    }
                }
            }
        }

        // Async validation through parent function
        if (options.validateFunction && typeof options.validateFunction === 'function') {
            try {
                const result = await options.validateFunction(state.code, state.language);
                if (result?.errors?.length) {
                    state.errors = result.errors.map(e => ({
                      line: e?.line || 0,
                      message: e?.message || 'Unknown'
                    }));
                  }
                if (result && result.errors && Array.isArray(result.errors)) {
                    state.errors = [...state.errors, ...result.errors];
                }
            } catch (e) {
                console.warn('Validation function error:', e);
            }
        }

        // Update UI with results
        requestAnimationFrame(() => {
            errorDisplay.innerHTML = state.errors.length > 0 ? `<strong>Error on line:</strong> ${state.errors.map(e => `Line ${e.line}: ${e.message}`).join('<br>')}` : '';
            errorDisplay.classList.toggle('hidden', state.errors.length === 0);
            updateLineNumbers();
        });

        el.dispatchEvent(new CustomEvent('code-validate', { detail: { code: state.code, errors: state.errors }, bubbles: true }));
    };

    const processUpdates = () => {
        render();
        updateLineNumbers();
    };

    const onInput = debounce((e) => {
        state.code = e.target.value;

        // Immediate UI updates for responsiveness
        requestAnimationFrame(() => {
            processUpdates();
        });

        // Debounced events
        //el.dispatchEvent(new CustomEvent('code-update', { detail: { code: state.code }, bubbles: true }));
        if (debouncedUpdateHandler) debouncedUpdateHandler(state.code);
        if (debouncedChangeHandler) debouncedChangeHandler(state.code);
        if (debouncedValidationHandler) debouncedValidationHandler();
    }, state.inputDebounce);

    const onBlur = () => {
        if (debouncedBlurHandler) debouncedBlurHandler();
    };

    const onScroll = (e) => {
        requestAnimationFrame(() => {
            highlightContainer.scrollTop = e.target.scrollTop;
            lineNumbersEl.scrollTop = e.target.scrollTop;
        });
    };

    const onKeyDown = (e) => {
        if (e.key === 'Tab') {
            e.preventDefault();
            editor.setRangeText('  ', editor.selectionStart, editor.selectionEnd, 'end');
            onInput({ target: editor });
        }
    };

    const onSearch = (e) => {
        if(e.target.value && e.target.value.length >= 3) {
            state.searchTerm = e.target.value;
        } else {
            state.searchTerm = '';
        }

        requestAnimationFrame(() => {
            render();
        });
    };

    const onClear = () => {
        editor.value = '';
        onInput({ target: editor });
        editor.focus();
    };

    const onFormat = () => {
        if (state.language !== 'json') return;
        try {
            editor.value = JSON.stringify(JSON.parse(state.code), null, 2);
            onInput({ target: editor });
        } catch (e) {
            formatBtn.textContent = 'Invalid JSON!';
            setTimeout(() => { formatBtn.textContent = 'Format'; }, 2000);
        }
    };

    editor.addEventListener('input', onInput);
    editor.addEventListener('blur', onBlur);
    editor.addEventListener('scroll', onScroll);
    editor.addEventListener('keydown', onKeyDown);
    searchInput.addEventListener('input', onSearch);
    clearBtn.addEventListener('click', onClear);
    formatBtn.addEventListener('click', onFormat);

    const configureToolbar = () => {
        searchInput.classList.toggle('hidden', !options.showSearch);
        clearBtn.classList.toggle('hidden', !options.showToolbar);
        formatBtn.classList.toggle('hidden', !options.showToolbar || state.language !== 'json');
        toolbar.classList.toggle('hidden', !options.showToolbar && !options.showSearch);
    };

    effect(() => {
        const newConfig = evaluate(expression) || {};
        options = {
            language: 'javascript',
            debounce: 250,
            blurDebounce: 100,
            validationDebounce: 500,
            validateFunction: null,
            ...newConfig
        };

        // Create properly debounced handlers
        debouncedUpdateHandler = debounce((code) => {
            el.dispatchEvent(new CustomEvent('code-update', { detail: { code }, bubbles: true }));
        }, options.debounce);

        debouncedChangeHandler = debounce((code) => {
            el.dispatchEvent(new CustomEvent('code-change', { detail: { code, debounce: options.debounce }, bubbles: true }));
        }, options.debounce);

        debouncedBlurHandler = debounce(() => {
            el.dispatchEvent(new CustomEvent('code-blur', { detail: { code: state.code }, bubbles: true }));
        }, options.blurDebounce);

        debouncedValidationHandler = debounce(() => {
            validate();
        }, options.validationDebounce);

        if(options.initialCode && typeof options.initialCode === 'function') {
            if(!state.code) {
                Promise.resolve(options.initialCode()).then(function(value) {
                    state.code = value;
                    editor.value = state.code;
                    processUpdates();
                });
            }
        }

        /*
        if (state.code !== options.initialCode) {
            console.info("case2");
            state.code = options.initialCode;
            editor.value = state.code;
        }
        */
        state.language = options.language;

        editor.placeholder = `// Your ${state.language} code here`;
        editorContainer.style.height = options.height;
        lineNumbersEl.classList.toggle('hidden', !options.showLineNumbers);

        configureToolbar();
        processUpdates();

        if (!isInitialized) {
            el.dispatchEvent(new CustomEvent('editor-ready', { bubbles: true }));
            isInitialized = true;
        }
    });

    cleanup(() => {
        // Cancel any pending debounced calls
        if (debouncedUpdateHandler && debouncedUpdateHandler.cancel) debouncedUpdateHandler.cancel();
        if (debouncedChangeHandler && debouncedChangeHandler.cancel) debouncedChangeHandler.cancel();
        if (debouncedBlurHandler && debouncedBlurHandler.cancel) debouncedBlurHandler.cancel();
        if (debouncedValidationHandler && debouncedValidationHandler.cancel) debouncedValidationHandler.cancel();

        // Remove all listeners
        editor.removeEventListener('input', onInput);
        editor.removeEventListener('blur', onBlur);
        editor.removeEventListener('scroll', onScroll);
        editor.removeEventListener('keydown', onKeyDown);
        searchInput.removeEventListener('input', onSearch);
        clearBtn.removeEventListener('click', onClear);
        formatBtn.removeEventListener('click', onFormat);
        el.innerHTML = '';
    });
});

function debounce(func, delay) {
    let timeoutId = String(func);
    const debounced = (...args) => {
        clearTimeout(debounceContext[timeoutId]);
        debounceContext[timeoutId] = setTimeout(() => func.apply(this, args), delay);
    };
    debounced.cancel = () => {
        clearTimeout(debounceContext[timeoutId]);
        debounceContext[timeoutId] = null
        delete debounceContext[timeoutId];
    };

    return debounced;
}

}

