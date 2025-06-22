export function initUiElementDirective() {
    Alpine.directive('ui-element', (el, { expression }, { evaluateLater, effect, cleanup }) => {
        const getElement = evaluateLater(expression);

        getElement(element => {
            if (!element) return;

            let html = '';
            let inputElement;
            let objId = `${element.name}-${Math.random().toString(36).slice(2)}`;

            switch (element.type) {
                case 'input':
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <input type="${element.inputType || 'text'}"
                                   id=${objId}
                                   placeholder="${element.placeholder || ''}"
                                   class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500"
                                   ${element.required ? 'required' : ''}>
                        </div>
                    `;
                    break;
                case 'textarea':
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <textarea placeholder="${element.placeholder || ''}"
                                      id=${objId}
                                      rows="${element.rows || 3}"
                                      class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500"
                                      ${element.required ? 'required' : ''}></textarea>
                        </div>
                    `;
                    break;
                case 'select':
                    const options = (element.options || []).map(opt =>
                        `<option value="${opt.value}">${opt.label}</option>`
                    ).join('');
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <select class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500"
                                    id=${objId}
                                    ${element.required ? 'required' : ''}>
                                <option value="">Select...</option>
                                ${options}
                            </select>
                        </div>
                    `;
                    break;
                case 'checkbox':
                    html = `
                        <div class="flex items-center space-x-2">
                            <input type="checkbox"
                                   class="w-4 h-4 text-purple-600 focus:ring-purple-500 border-gray-300 rounded"
                                   id=${objId}>
                            <label class="text-sm font-medium text-gray-700">${element.label || element.name}</label>
                        </div>
                    `;
                    break;
                case 'display':
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <div class="px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg">
                                <span id=${objId}>${element.placeholder || 'No data'}</span>
                            </div>
                        </div>
                    `;
                    break;
                default:
                    html = `
                        <div class="text-red-500">
                            Unknown element type: ${element.type}
                        </div>
                    `;
            }

            el.innerHTML = html;

            const handleRefresh = (event) => {
                const alpineComponent = el.closest('[x-data]');
                const sourceObj = Alpine.$data(alpineComponent)[element.source];
                if (element.type === 'display') {
                    el.querySelector('span').textContent = sourceObj[element.name] || element.placeholder || 'No data';
                } else {
                    if (element.type === 'checkbox') {
                        inputElement.checked = sourceObj[element.name] || false;
                    } else {
                        inputElement.value = sourceObj[element.name] || '';
                    }
                }
            };

            if(element.source) {
                // Get the input element and set up vanilla JS event handling
                inputElement = el.querySelector('input, textarea, select');

                if (inputElement) {
                    // Get the source object from Alpine's scope
                    const alpineComponent = el.closest('[x-data]');
                    const sourceObj = Alpine.$data(alpineComponent)[element.source];

                    if (sourceObj) {
                        // Set initial value
                        const updateInputValue = () => {
                            if (element.type === 'checkbox') {
                                inputElement.checked = sourceObj[element.name] || false;
                            } else {
                                inputElement.value = sourceObj[element.name] || '';
                            }
                        };

                        updateInputValue();

                        // Handle value changes from input to source
                        const updateSource = () => {
                            if (element.type === 'checkbox') {
                                sourceObj[element.name] = inputElement.checked;
                            } else {
                                sourceObj[element.name] = inputElement.value;
                            }
                        };

                        inputElement.addEventListener('input', updateSource);
                        inputElement.addEventListener('change', updateSource);

                        // Listen for external changes to source data
                        const observer = new MutationObserver(() => {
                            updateInputValue();
                        });

                        // Watch for changes in the source object
                        if (typeof Proxy !== 'undefined') {
                            const watchEffect = Alpine.effect(() => {
                                // Access the property to register dependency
                                const value = sourceObj[element.name];
                                updateInputValue();
                            });

                            // Store cleanup function
                            if (!el._x_cleanups) el._x_cleanups = [];
                            el._x_cleanups.push(watchEffect);
                        }
                    }
                }

                // Handle display elements
                if (element.type === 'display') {
                    const alpineComponent = el.closest('[x-data]');
                    const sourceObj = Alpine.$data(alpineComponent)[element.source];
                    const displaySpan = el.querySelector('span');

                    const updateDisplayValue = () => {
                        displaySpan.textContent = sourceObj[element.name] || element.placeholder || 'No data';
                    };

                    if (sourceObj && displaySpan) {
                        updateDisplayValue();

                        // Listen for external changes to source data
                        if (typeof Proxy !== 'undefined') {
                            const watchEffect = Alpine.effect(() => {
                                // Access the property to register dependency
                                const value = sourceObj[element.name];
                                updateDisplayValue();
                            });

                            // Store cleanup function
                            if (!el._x_cleanups) el._x_cleanups = [];
                            el._x_cleanups.push(watchEffect);
                        }
                    }
                }

                document.addEventListener('ui-element-refresh', handleRefresh);
            }
        });

        // Cleanup function
        cleanup(() => {
            if (el._x_cleanups) {
                el._x_cleanups.forEach(cleanupFn => cleanupFn());
                el._x_cleanups = [];
            }
            document.removeEventListener('ui-element-refresh', handleRefresh);
        });
    });
}