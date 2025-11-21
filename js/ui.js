// MODAL & UI HELPERS
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
