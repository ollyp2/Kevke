/**
 * MAIN.JS
 * Application initialization and event listeners
 *
 * Dependencies: All other modules
 * This is the entry point - loads data and sets up all event handlers
 */

document.addEventListener('DOMContentLoaded', function() {
    // Event Listeners
    document.getElementById('calcToggle').onclick = toggleCalculator;
    document.getElementById('dateCalcToggle').onclick = openDateCalcModal;
    document.getElementById('avgToggle').onclick = openAvgModal;
    document.getElementById('timerWidget').onclick = openTimerModal;
    document.getElementById('editToggle').onclick = toggleEditMode;
    document.getElementById('addBtn').onclick = addNewButton;
    document.getElementById('addOfferBtn').onclick = openAddOfferModal;
    document.getElementById('createBtn').onclick = createNote;
    document.getElementById('noteInput').addEventListener('keypress', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            createNote();
        }
    });
    document.getElementById('timeToggle').onclick = toggleTimeVisibility;
    document.getElementById('fileHeader').onclick = toggleUpload;
    document.getElementById('uploadArea').onclick = function() {
        document.getElementById('fileInput').click();
    };
    document.getElementById('fileInput').onchange = function() {
        handleFiles(this.files);
    };
    document.getElementById('closeBtn').onclick = closeModal;
    document.getElementById('modal').onclick = function(e) {
        if (e.target === document.getElementById('modal')) {
            closeModal();
        }
    };
    document.getElementById('dismissAllExpiredBtn').onclick = dismissAllExpiredTimers;

    // INITIALIZATION
    loadData();
    setupDragAndDrop();
    setupCalculator();
    renderButtons();
    renderNotes();
    renderFiles();
    renderOffers();
    updateClock();
    setInterval(updateClock, 1000);
    requestNotification();

    // Request notification permission on load
    if ("Notification" in window && Notification.permission === "default") {
        setTimeout(function() {
            Notification.requestPermission();
        }, 2000);
    }
});
