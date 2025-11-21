/**
 * CONFIG.JS
 * Central configuration file for all modules
 *
 * Purpose: Single source of truth for constants, avoiding magic numbers/strings
 * Used by: All modules
 */

const CONFIG = {
    // LocalStorage keys
    STORAGE_KEYS: {
        NOTES: 'workdesk_notes',
        NOTE_TITLES: 'workdesk_note_titles',
        FILES: 'workdesk_files',
        BUTTONS: 'workdesk_buttons',
        AVG_VALUES: 'workdesk_avg_values',
        OFFERS: 'workdesk_offers'
    },

    // Timer defaults
    TIMER: {
        DEFAULT_COUNTDOWN_MINUTES: 5,
        UPDATE_INTERVAL_MS: 1000,
        NOTIFICATION_DELAY_MS: 2000
    },

    // Calculator defaults
    CALCULATOR: {
        DEFAULT_VALUE: '0',
        MAX_INPUT_LENGTH: 50
    },

    // File upload
    FILES: {
        ACCEPTED_TYPES: ['application/pdf', 'image/png', 'image/jpeg', 'image/jpg'],
        ACCEPTED_EXTENSIONS: ['.pdf', '.png', '.jpg', '.jpeg']
    },

    // Offers status thresholds
    OFFERS: {
        EXPIRING_DAYS_THRESHOLD: 7
    },

    // UI timing
    UI: {
        FOCUS_DELAY_MS: 100,
        CLOCK_UPDATE_INTERVAL_MS: 1000
    }
};
