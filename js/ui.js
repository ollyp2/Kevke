/**
 * UI.JS
 * Modal and UI helper functions
 *
 * Dependencies: calculator.js (for closing calc on modal open)
 * Used by: All modules that show modals
 */

function showModal(title, body) {
    // Close calculator when opening modal
    if (calcActive) {
        toggleCalculator();
    }
    document.getElementById('modalTitleContainer').innerHTML = '<h2 class="modal-title">' + title + '</h2>';
    document.getElementById('modalBody').innerHTML = body;
    document.getElementById('modal').classList.add('open');
}

function closeModal() {
    document.getElementById('modal').classList.remove('open');
    currentNoteIndex = -1;
}
