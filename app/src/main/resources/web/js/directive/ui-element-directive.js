export function initUiElementDirective() {
    Alpine.directive('ui-element', (el, { expression }, { evaluateLater, effect, cleanup }) => {
        const getElement = evaluateLater(expression);

        getElement(element => {
            if (!element) return;

            let html = '';
            let inputElement;

            switch (element.type) {
                case 'input':
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <input type="${element.inputType || 'text'}"
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
                                   class="w-4 h-4 text-purple-600 focus:ring-purple-500 border-gray-300 rounded">
                            <label class="text-sm font-medium text-gray-700">${element.label || element.name}</label>
                        </div>
                    `;
                    break;
                case 'display':
                    html = `
                        <div class="space-y-2">
                            <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            <div class="px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg">
                                <span>${element.placeholder || 'No data'}</span>
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

            // Get the input element and set up vanilla JS event handling
            inputElement = el.querySelector('input, textarea, select');

            if (inputElement && element.source) {
                // Get the source object from Alpine's scope
                const sourceObj = Alpine.$data(el.closest('[x-data]'))[element.source];

                if (sourceObj) {
                    // Set initial value
                    if (element.type === 'checkbox') {
                        inputElement.checked = sourceObj[element.name] || false;
                    } else {
                        inputElement.value = sourceObj[element.name] || '';
                    }

                    // Handle value changes
                    const updateSource = () => {
                        if (element.type === 'checkbox') {
                            sourceObj[element.name] = inputElement.checked;
                        } else {
                            sourceObj[element.name] = inputElement.value;
                        }
                    };

                    inputElement.addEventListener('input', updateSource);
                    inputElement.addEventListener('change', updateSource);
                }
            }

            // Handle display elements
            if (element.type === 'display' && element.source) {
                const sourceObj = Alpine.$data(el.closest('[x-data]'))[element.source];
                const displaySpan = el.querySelector('span');

                if (sourceObj && displaySpan) {
                    displaySpan.textContent = sourceObj[element.name] || element.placeholder || 'No data';
                }
            }
        });
    });
}