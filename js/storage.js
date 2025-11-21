// DATA MANAGEMENT
// Global state variables
var notes = [];
var noteTitles = [];
var files = [];
var buttons = [];
var avgValues = [];
var offers = [];
var expandedOffers = [];
var isEditMode = false;

function saveData() {
    localStorage.setItem('workdesk_notes', JSON.stringify(notes));
    localStorage.setItem('workdesk_note_titles', JSON.stringify(noteTitles));
    localStorage.setItem('workdesk_files', JSON.stringify(files));
    localStorage.setItem('workdesk_buttons', JSON.stringify(buttons));
    localStorage.setItem('workdesk_avg_values', JSON.stringify(avgValues));
    localStorage.setItem('workdesk_offers', JSON.stringify(offers));
}

function loadData() {
    try {
        notes = JSON.parse(localStorage.getItem('workdesk_notes') || '[]');
        noteTitles = JSON.parse(localStorage.getItem('workdesk_note_titles') || '[]');
        files = JSON.parse(localStorage.getItem('workdesk_files') || '[]');
        buttons = JSON.parse(localStorage.getItem('workdesk_buttons') || '[]');
        avgValues = JSON.parse(localStorage.getItem('workdesk_avg_values') || '[]');
        offers = JSON.parse(localStorage.getItem('workdesk_offers') || '[]');
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
