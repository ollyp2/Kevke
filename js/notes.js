/**
 * NOTES.JS
 * Notes management - create, edit, delete, drag-and-drop reordering
 *
 * Dependencies: ui.js, storage.js
 * Used by: main.js
 */

var currentNoteIndex = -1;
var draggedNoteIndex = null;

function createNote() {
    var content = document.getElementById('noteInput').value.trim();
    if (!content) {
        alert('Please enter some text for your note');
        return;
    }
    notes.push(content);
    noteTitles.push('Note #' + notes.length);
    document.getElementById('noteInput').value = '';
    saveData();
    renderNotes();
}

function renderNotes() {
    var notesBar = document.getElementById('notesBar');
    notesBar.innerHTML = '';
    notes.forEach(function(note, index) {
        var wrapper = document.createElement('div');
        wrapper.className = 'note-button-container';
        wrapper.dataset.index = index;
        wrapper.draggable = true;

        wrapper.ondragstart = function(e) {
            draggedNoteIndex = parseInt(this.dataset.index);
            this.style.opacity = '0.4';
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/html', 'note');
        };

        wrapper.ondragend = function(e) {
            this.style.opacity = '1';
            this.style.borderLeft = '';
        };

        wrapper.ondragover = function(e) {
            if (draggedNoteIndex === null) return;
            if (e.preventDefault) e.preventDefault();
            this.style.borderLeft = '3px solid #dc3c3c';
            return false;
        };

        wrapper.ondragleave = function(e) {
            this.style.borderLeft = '';
        };

        wrapper.ondrop = function(e) {
            if (draggedNoteIndex === null) return false;
            if (e.stopPropagation) e.stopPropagation();
            this.style.borderLeft = '';

            var dropIndex = parseInt(this.dataset.index);
            if (draggedNoteIndex !== dropIndex) {
                // Swap positions
                var tempNote = notes[draggedNoteIndex];
                var tempTitle = noteTitles[draggedNoteIndex];

                notes[draggedNoteIndex] = notes[dropIndex];
                noteTitles[draggedNoteIndex] = noteTitles[dropIndex];

                notes[dropIndex] = tempNote;
                noteTitles[dropIndex] = tempTitle;

                saveData();
                renderNotes();
            }
            draggedNoteIndex = null;
            return false;
        };

        var button = document.createElement('button');
        button.className = 'note-button';
        button.textContent = noteTitles[index] || ('Note #' + (index + 1));
        button.onclick = function(e) {
            if (!e.target.classList.contains('note-delete-btn')) {
                openNote(parseInt(wrapper.dataset.index));
            }
        };

        // Delete button for notes in edit mode
        var deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn note-delete-btn';
        deleteBtn.innerHTML = '&times;';
        deleteBtn.onclick = function(e) {
            e.stopPropagation();
            var idx = parseInt(wrapper.dataset.index);
            if (confirm('Diese Notiz löschen?')) {
                notes.splice(idx, 1);
                noteTitles.splice(idx, 1);
                saveData();
                renderNotes();
            }
        };

        wrapper.appendChild(button);
        wrapper.appendChild(deleteBtn);
        notesBar.appendChild(wrapper);
    });
}

function openNote(index) {
    currentNoteIndex = index;
    showModal(noteTitles[index] || ('Note #' + (index + 1)),
        '<div class="note-content">' + notes[index] + '</div>' +
        '<div class="modal-actions"><button class="action-button edit-button" id="editNoteBtn">Edit</button><button class="action-button delete-button" id="delNoteBtn">Delete</button></div>'
    );
    document.getElementById('editNoteBtn').onclick = function() { openNoteEdit(index); };
    document.getElementById('delNoteBtn').onclick = function() { deleteNoteFromModal(index); };
}

function openNoteEdit(index) {
    currentNoteIndex = index;
    document.getElementById('modalTitleContainer').innerHTML = '<input type="text" class="modal-title-input" id="titleInput" value="' + (noteTitles[index] || ('Note #' + (index + 1))) + '">';
    document.getElementById('modalBody').innerHTML =
        '<textarea class="note-content-edit" id="editTextarea">' + notes[index] + '</textarea>' +
        '<div class="modal-actions"><button class="action-button save-button" id="saveNoteBtn">Save</button><button class="action-button cancel-button" id="cancelNoteBtn">Cancel</button></div>';
    document.getElementById('saveNoteBtn').onclick = saveNoteEdit;
    document.getElementById('cancelNoteBtn').onclick = closeModal;
    setTimeout(function() { document.getElementById('editTextarea').focus(); }, 100);
}

function saveNoteEdit() {
    var newContent = document.getElementById('editTextarea').value.trim();
    var newTitle = document.getElementById('titleInput').value.trim();
    if (!newContent) {
        alert('Note cannot be empty');
        return;
    }
    if (!newTitle) {
        alert('Title cannot be empty');
        return;
    }
    notes[currentNoteIndex] = newContent;
    noteTitles[currentNoteIndex] = newTitle;
    saveData();
    renderNotes();
    closeModal();
}

function deleteNoteFromModal(index) {
    if (confirm('Delete this note?')) {
        notes.splice(index, 1);
        noteTitles.splice(index, 1);
        saveData();
        renderNotes();
        closeModal();
    }
}
