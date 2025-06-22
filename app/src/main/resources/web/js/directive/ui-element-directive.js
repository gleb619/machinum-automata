export function initUiElementDirective() {
    Alpine.directive('ui-element', (el, { expression }, { evaluateLater, effect }) => {
        const getElement = evaluateLater(expression);

        effect(() => {
            getElement(element => {
                if (!element) return;

                let html = '';

                switch (element.type) {
                    case 'input':
                        html = `
                            <div class="space-y-2">
                                <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                                <input type="${element.inputType || 'text'}"
                                       placeholder="${element.placeholder || ''}"
                                       class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500"
                                       x-model="${element.source}['${element.name}']"
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
                                          x-model="${element.source}['${element.name}']"
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
                                        x-model="${element.source}['${element.name}']"
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
                                       x-model="${element.source}['${element.name}']">
                                <label class="text-sm font-medium text-gray-700">${element.label || element.name}</label>
                            </div>
                        `;
                        break;
                    case 'display':
                        html = `
                            <div class="space-y-2">
                                <label class="block text-sm font-medium text-gray-700">${element.label || element.name}</label>
                                <div class="px-3 py-2 bg-gray-50 border border-gray-200 rounded-lg">
                                    <span x-text="${element.source}['${element.name}'] || '${element.placeholder || 'No data'}'"></span>
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
            });
        });
    });
}