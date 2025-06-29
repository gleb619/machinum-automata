export function uiConfigApp() {
  return {
    uiPlaceholder: `
\/\/ UI Configuration JSON Format:
[
  {
    "type": "input",
    "name": "username",
    "label": "Username",
    "placeholder": "Enter username",
    "required": true,
    "source": "uiData"
  }
]   `,
    loadExample(type) {
        const examples = {
            login: [
                { type: 'input', name: 'username', label: 'Username', placeholder: 'Enter your username', required: true, source: 'uiData' },
                { type: 'input', name: 'password', label: 'Password', inputType: 'password', placeholder: 'Enter your password', required: true, source: 'uiData' },
                { type: 'checkbox', name: 'remember', label: 'Remember me', source: 'uiData' }
            ],
            search: [
                { type: 'input', name: 'query', label: 'Search', placeholder: 'Enter search terms...', required: true, source: 'uiData' },
                { type: 'select', name: 'category', label: 'Category', options: [
                    { value: 'all', label: 'All Categories' },
                    { value: 'products', label: 'Products' },
                    { value: 'articles', label: 'Articles' },
                    { value: 'users', label: 'Users' }
                ], source: 'uiData'},
                { type: 'checkbox', name: 'exact_match', label: 'Exact match only', source: 'uiData' }
            ],
            form: [
                { type: 'input', name: 'name', label: 'Full Name', placeholder: 'Your full name', required: true, source: 'uiData' },
                { type: 'input', name: 'email', label: 'Email', inputType: 'email', placeholder: 'your@email.com', required: true, source: 'uiData' },
                { type: 'input', name: 'phone', label: 'Phone', inputType: 'tel', placeholder: '+1 (555) 123-4567', source: 'uiData' },
                { type: 'textarea', name: 'message', label: 'Message', placeholder: 'Your message here...', rows: 4, required: true, source: 'uiData' }
            ],
            data: [
                { type: 'input', name: 'title', label: 'Title', placeholder: 'Enter title', required: true, source: 'uiData' },
                { type: 'textarea', name: 'description', label: 'Description', placeholder: 'Enter description', rows: 3, source: 'uiData' },
                { type: 'select', name: 'status', label: 'Status', options: [
                    { value: 'draft', label: 'Draft' },
                    { value: 'published', label: 'Published' },
                    { value: 'archived', label: 'Archived' }
                ], required: true, source: 'uiData' },
                { type: 'input', name: 'tags', label: 'Tags', placeholder: 'Comma-separated tags', source: 'uiData' }
            ],
            settings: [
                { type: 'checkbox', name: 'notifications', label: 'Enable notifications', source: 'uiData' },
                { type: 'checkbox', name: 'auto_save', label: 'Auto-save changes', source: 'uiData' },
                { type: 'select', name: 'theme', label: 'Theme', options: [
                    { value: 'light', label: 'Light' },
                    { value: 'dark', label: 'Dark' },
                    { value: 'auto', label: 'Auto' }
                ], source: 'uiData'},
                { type: 'select', name: 'language', label: 'Language', options: [
                    { value: 'en', label: 'English' },
                    { value: 'es', label: 'Spanish' },
                    { value: 'fr', label: 'French' }
                ], source: 'uiData'}
            ],
            filters: [
                { type: 'input', name: 'min_price', label: 'Min Price', inputType: 'number', placeholder: '0', source: 'uiData' },
                { type: 'input', name: 'max_price', label: 'Max Price', inputType: 'number', placeholder: '1000', source: 'uiData' },
                { type: 'select', name: 'brand', label: 'Brand', options: [
                    { value: 'apple', label: 'Apple' },
                    { value: 'samsung', label: 'Samsung' },
                    { value: 'google', label: 'Google' }
                ], source: 'uiData'},
                { type: 'checkbox', name: 'in_stock', label: 'In stock only', source: 'uiData' }
            ],
            wizard: [
                { type: 'display', name: 'step', label: 'Current Step', placeholder: 'Step 1 of 3', source: 'uiData' },
                { type: 'input', name: 'first_name', label: 'First Name', placeholder: 'Enter first name', required: true, source: 'uiData' },
                { type: 'input', name: 'last_name', label: 'Last Name', placeholder: 'Enter last name', required: true, source: 'uiData' },
                { type: 'select', name: 'country', label: 'Country', options: [
                    { value: 'us', label: 'United States' },
                    { value: 'ca', label: 'Canada' },
                    { value: 'uk', label: 'United Kingdom' }
                ], required: true, source: 'uiData' }
            ],
            dashboard: [
                { type: 'select', name: 'date_range', label: 'Date Range', options: [
                    { value: '7d', label: 'Last 7 days' },
                    { value: '30d', label: 'Last 30 days' },
                    { value: '90d', label: 'Last 90 days' }
                ], source: 'uiData'},
                { type: 'checkbox', name: 'show_trends', label: 'Show trends', source: 'uiData' },
                { type: 'checkbox', name: 'auto_refresh', label: 'Auto refresh', source: 'uiData' },
                { type: 'display', name: 'last_updated', label: 'Last Updated', placeholder: 'Never', source: 'uiData' }
            ],
            ecommerce: [
                { type: 'input', name: 'product_name', label: 'Product Name', placeholder: 'Enter product name', required: true, source: 'uiData' },
                { type: 'input', name: 'price', label: 'Price', inputType: 'number', placeholder: '0.00', required: true, source: 'uiData' },
                { type: 'select', name: 'category', label: 'Category', options: [
                    { value: 'electronics', label: 'Electronics' },
                    { value: 'clothing', label: 'Clothing' },
                    { value: 'books', label: 'Books' }
                ], required: true, source: 'uiData' },
                { type: 'checkbox', name: 'featured', label: 'Featured product', source: 'uiData' }
            ],
            profile: [
                { type: 'input', name: 'display_name', label: 'Display Name', placeholder: 'Your display name', required: true, source: 'uiData' },
                { type: 'input', name: 'bio', label: 'Bio', placeholder: 'Tell us about yourself', source: 'uiData' },
                { type: 'input', name: 'website', label: 'Website', inputType: 'url', placeholder: 'https://example.com', source: 'uiData' },
                { type: 'checkbox', name: 'public_profile', label: 'Make profile public', source: 'uiData' }
            ],
            admin: [
                { type: 'select', name: 'user_role', label: 'User Role', options: [
                    { value: 'admin', label: 'Administrator' },
                    { value: 'moderator', label: 'Moderator' },
                    { value: 'user', label: 'User' }
                ], required: true, source: 'uiData' },
                { type: 'checkbox', name: 'can_edit', label: 'Can edit content', source: 'uiData' },
                { type: 'checkbox', name: 'can_delete', label: 'Can delete content', source: 'uiData' },
                { type: 'display', name: 'permissions', label: 'Active Permissions', placeholder: 'None selected', source: 'uiData' }
            ],
            feedback: [
                { type: 'select', name: 'rating', label: 'Rating', options: [
                    { value: '5', label: '5 - Excellent' },
                    { value: '4', label: '4 - Good' },
                    { value: '3', label: '3 - Average' },
                    { value: '2', label: '2 - Poor' },
                    { value: '1', label: '1 - Terrible' }
                ], required: true, source: 'uiData' },
                { type: 'textarea', name: 'comments', label: 'Comments', placeholder: 'Share your feedback...', rows: 4, source: 'uiData' },
                { type: 'checkbox', name: 'recommend', label: 'Would recommend to others', source: 'uiData' }
            ],
            booking: [
                { type: 'input', name: 'check_in', label: 'Check-in Date', inputType: 'date', required: true, source: 'uiData' },
                { type: 'input', name: 'check_out', label: 'Check-out Date', inputType: 'date', required: true, source: 'uiData' },
                { type: 'select', name: 'guests', label: 'Number of Guests', options: [
                    { value: '1', label: '1 Guest' },
                    { value: '2', label: '2 Guests' },
                    { value: '3', label: '3 Guests' },
                    { value: '4', label: '4+ Guests' }
                ], required: true, source: 'uiData' },
                { type: 'textarea', name: 'special_requests', label: 'Special Requests', placeholder: 'Any special requirements...', rows: 3, source: 'uiData' }
            ],
            survey: [
                { type: 'select', name: 'age_group', label: 'Age Group', options: [
                    { value: '18-25', label: '18-25' },
                    { value: '26-35', label: '26-35' },
                    { value: '36-45', label: '36-45' },
                    { value: '46+', label: '46+' }
                ], required: true, source: 'uiData' },
                { type: 'checkbox', name: 'newsletter', label: 'Subscribe to newsletter', source: 'uiData' },
                { type: 'textarea', name: 'suggestions', label: 'Suggestions', placeholder: 'How can we improve?', rows: 3, source: 'uiData' }
            ],
            calculator: [
                { type: 'input', name: 'value_a', label: 'Value A', inputType: 'number', placeholder: '0', required: true, source: 'uiData' },
                { type: 'select', name: 'operation', label: 'Operation', options: [
                    { value: 'add', label: 'Add (+)' },
                    { value: 'subtract', label: 'Subtract (-)' },
                    { value: 'multiply', label: 'Multiply (×)' },
                    { value: 'divide', label: 'Divide (÷)' }
                ], required: true, source: 'uiData' },
                { type: 'input', name: 'value_b', label: 'Value B', inputType: 'number', placeholder: '0', required: true, source: 'uiData' },
                { type: 'display', name: 'result', label: 'Result', placeholder: 'Calculate to see result', source: 'uiData' }
            ],
            monitoring: [
                { type: 'display', name: 'status', label: 'System Status', placeholder: 'Unknown', source: 'uiData' },
                { type: 'display', name: 'uptime', label: 'Uptime', placeholder: '0 days', source: 'uiData' },
                { type: 'display', name: 'cpu_usage', label: 'CPU Usage', placeholder: '0%', source: 'uiData' },
                { type: 'display', name: 'memory_usage', label: 'Memory Usage', placeholder: '0%', source: 'uiData' },
                { type: 'checkbox', name: 'alerts_enabled', label: 'Enable alerts', source: 'uiData' }
            ],
            translation: [
                { type: 'select', name: 'source_language', label: 'Source Language', options: [
                    { value: 'en', label: 'English' },
                    { value: 'es', label: 'Spanish' },
                    { value: 'fr', label: 'French' },
                    { value: 'de', label: 'German' },
                    { value: 'it', label: 'Italian' },
                    { value: 'pt', label: 'Portuguese' },
                    { value: 'ru', label: 'Russian' },
                    { value: 'zh', label: 'Chinese' },
                    { value: 'ja', label: 'Japanese' },
                    { value: 'ko', label: 'Korean' }
                ], required: true, source: 'uiData' },
                { type: 'select', name: 'target_language', label: 'Target Language', options: [
                    { value: 'en', label: 'English' },
                    { value: 'es', label: 'Spanish' },
                    { value: 'fr', label: 'French' },
                    { value: 'de', label: 'German' },
                    { value: 'it', label: 'Italian' },
                    { value: 'pt', label: 'Portuguese' },
                    { value: 'ru', label: 'Russian' },
                    { value: 'zh', label: 'Chinese' },
                    { value: 'ja', label: 'Japanese' },
                    { value: 'ko', label: 'Korean' }
                ], required: true, source: 'uiData' },
                { type: 'textarea', name: 'source_text', label: 'Text to Translate', placeholder: 'Enter text to translate...', rows: 5, required: true, source: 'uiData' },
                { type: 'textarea', name: 'translated_text', label: 'Translation', placeholder: 'Translation will appear here...', rows: 5, source: 'uiData' },
                { type: 'checkbox', name: 'auto_detect', label: 'Auto-detect source language', source: 'uiData' }
            ]
        };

        this.script.uiConfig = this.jsonStringify(examples[type] || []);
        this.uiData = {};
        examples[type]?.forEach(element => {
            this.uiData[element.name] = element.type === 'checkbox' ? false : '';
        });
    },

    addElementExample(type) {
        const elementExamples = {
            input: { type: 'input', name: 'sample_input', label: 'Sample Input', placeholder: 'Enter text here...', required: false },
            textarea: { type: 'textarea', name: 'sample_textarea', label: 'Sample Textarea', placeholder: 'Enter longer text...', rows: 3 },
            select: {
                type: 'select',
                name: 'sample_select',
                label: 'Sample Select',
                options: [
                    { value: 'option1', label: 'Option 1' },
                    { value: 'option2', label: 'Option 2' },
                    { value: 'option3', label: 'Option 3' }
                ],
                required: false
            },
            checkbox: { type: 'checkbox', name: 'sample_checkbox', label: 'Sample Checkbox' },
            radio: {
                type: 'select',
                name: 'sample_radio',
                label: 'Sample Radio Group',
                options: [
                    { value: 'choice1', label: 'Choice 1' },
                    { value: 'choice2', label: 'Choice 2' },
                    { value: 'choice3', label: 'Choice 3' }
                ],
                required: false
            },
            number: { type: 'input', name: 'sample_number', label: 'Sample Number', inputType: 'number', placeholder: '0', required: false },
            date: { type: 'input', name: 'sample_date', label: 'Sample Date', inputType: 'date', required: false },
            display: { type: 'display', name: 'sample_display', label: 'Sample Display', placeholder: 'Display value here' }
        };

        const element = elementExamples[type];
        if (element) {
            const tempArray = JSON.parse(this.script.uiConfig || '[]') || [];
            tempArray.push(element);
            this.script.uiConfig = this.jsonStringify(tempArray);
            this.uiData[element.name] = element.type === 'checkbox' ? false : '';
        }
    }
  }
};

