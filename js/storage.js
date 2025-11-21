/**
 * STORAGE.JS
 * Data management and localStorage persistence
 *
 * Dependencies: config.js
 * Used by: All modules that need to save/load data
 */

// Global state variables
var notes = [];
var noteTitles = [];
var files = [];
var buttons = [];
var avgValues = [];
var offers = [];
var expandedOffers = [];
var isEditMode = false;

/**
 * Save all data to localStorage
 * Called after any data modification
 */
function saveData() {
    localStorage.setItem(CONFIG.STORAGE_KEYS.NOTES, JSON.stringify(notes));
    localStorage.setItem(CONFIG.STORAGE_KEYS.NOTE_TITLES, JSON.stringify(noteTitles));
    localStorage.setItem(CONFIG.STORAGE_KEYS.FILES, JSON.stringify(files));
    localStorage.setItem(CONFIG.STORAGE_KEYS.BUTTONS, JSON.stringify(buttons));
    localStorage.setItem(CONFIG.STORAGE_KEYS.AVG_VALUES, JSON.stringify(avgValues));
    localStorage.setItem(CONFIG.STORAGE_KEYS.OFFERS, JSON.stringify(offers));
}

/**
 * Load all data from localStorage
 * Called on page init
 */
function loadData() {
    try {
        notes = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.NOTES) || '[]');
        noteTitles = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.NOTE_TITLES) || '[]');
        files = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.FILES) || '[]');
        buttons = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.BUTTONS) || '[]');
        avgValues = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.AVG_VALUES) || '[]');
        offers = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.OFFERS) || '[]');
    } catch (e) {
        notes = [];
        noteTitles = [];
        files = [];
        buttons = [];
        avgValues = [];
        offers = [];
    }
}

function toggleEditMode() {
    isEditMode = !isEditMode;
    var toggle = document.getElementById('editToggle');
    if (isEditMode) {
        document.body.classList.add('edit-mode');
        toggle.classList.add('active');
        toggle.textContent = 'View Mode';
    } else {
        document.body.classList.remove('edit-mode');
        toggle.classList.remove('active');
        toggle.textContent = 'Edit Mode';
    }
    renderOffers();
}
