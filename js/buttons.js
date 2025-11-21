/**
 * BUTTONS.JS
 * Navigation buttons management - add, edit, delete, drag-and-drop reordering
 *
 * Dependencies: ui.js, storage.js
 * Used by: main.js
 */

var draggedButtonIndex = null;

function updateAddButtonVisibility() {
    var addBtn = document.getElementById('addBtn');
    if (buttons.length === 0) {
        addBtn.classList.add('show-always');
    } else {
        addBtn.classList.remove('show-always');
    }
}

function renderButtons() {
    var container = document.getElementById('navButtons');
    container.innerHTML = '';
    buttons.forEach(function(btn, index) {
        var wrapper = document.createElement('div');
        wrapper.className = 'nav-button-container';
        wrapper.dataset.index = index;

        // Only make draggable in edit mode
        if (isEditMode) {
            wrapper.draggable = true;
        }

        wrapper.ondragstart = function(e) {
            if (!isEditMode) return false;
            draggedButtonIndex = parseInt(this.dataset.index);
            this.style.opacity = '0.4';
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/html', 'button');
        };

        wrapper.ondragend = function(e) {
            this.style.opacity = '1';
            this.style.borderLeft = '';
        };

        wrapper.ondragover = function(e) {
            if (!isEditMode || draggedButtonIndex === null) return;
            if (e.preventDefault) e.preventDefault();
            this.style.borderLeft = '3px solid #dc3c3c';
            return false;
        };

        wrapper.ondragleave = function(e) {
            this.style.borderLeft = '';
        };

        wrapper.ondrop = function(e) {
            if (!isEditMode || draggedButtonIndex === null) return false;
            if (e.stopPropagation) e.stopPropagation();
            this.style.borderLeft = '';

            var dropIndex = parseInt(this.dataset.index);
            if (draggedButtonIndex !== dropIndex) {
                // Swap positions
                var temp = buttons[draggedButtonIndex];
                buttons[draggedButtonIndex] = buttons[dropIndex];
                buttons[dropIndex] = temp;

                saveData();
                renderButtons();
            }
            draggedButtonIndex = null;
            return false;
        };

        var button = document.createElement('a');
        button.className = 'nav-button';
        button.href = btn.url;
        button.target = '_blank';
        button.textContent = btn.name;
        button.onclick = function(e) {
            if (isEditMode) {
                e.preventDefault();
                editButton(parseInt(wrapper.dataset.index));
            }
        };

        var deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.innerHTML = '&times;';
        deleteBtn.onclick = function(e) {
            e.stopPropagation();
            deleteButton(parseInt(wrapper.dataset.index));
        };

        wrapper.appendChild(button);
        wrapper.appendChild(deleteBtn);
        container.appendChild(wrapper);
    });
    updateAddButtonVisibility();
}

function addNewButton() {
    showModal('Add New Button',
        '<div class="form-group"><label class="form-label">Button Name</label><input type="text" class="form-input" id="btnName" placeholder="Enter button name"></div>' +
        '<div class="form-group"><label class="form-label">URL</label><input type="text" class="form-input" id="btnUrl" placeholder="https://example.com" value="about:blank"></div>' +
        '<div class="modal-actions"><button class="action-button save-button" id="saveBtnBtn">Add</button><button class="action-button cancel-button" id="cancelBtnBtn">Cancel</button></div>'
    );
    document.getElementById('saveBtnBtn').onclick = saveNewButton;
    document.getElementById('cancelBtnBtn').onclick = closeModal;
    setTimeout(function() { document.getElementById('btnName').focus(); }, 100);
}

function saveNewButton() {
    var name = document.getElementById('btnName').value.trim();
    var url = document.getElementById('btnUrl').value.trim();
    if (!name) {
        alert('Please enter a button name');
        return;
    }
    buttons.push({ name: name, url: url });
    saveData();
    renderButtons();
    closeModal();
}

function editButton(index) {
    showModal('Edit Button',
        '<div class="form-group"><label class="form-label">Button Name</label><input type="text" class="form-input" id="btnName" value="' + buttons[index].name + '"></div>' +
        '<div class="form-group"><label class="form-label">URL</label><input type="text" class="form-input" id="btnUrl" value="' + buttons[index].url + '"></div>' +
        '<div class="modal-actions"><button class="action-button save-button" id="saveEditBtn">Save</button><button class="action-button cancel-button" id="cancelEditBtn">Cancel</button></div>'
    );
    document.getElementById('saveEditBtn').onclick = function() { updateButton(index); };
    document.getElementById('cancelEditBtn').onclick = closeModal;
    setTimeout(function() { document.getElementById('btnName').focus(); }, 100);
}

function updateButton(index) {
    var name = document.getElementById('btnName').value.trim();
    var url = document.getElementById('btnUrl').value.trim();
    if (!name) {
        alert('Please enter a button name');
        return;
    }
    buttons[index] = { name: name, url: url };
    saveData();
    renderButtons();
    closeModal();
}

function deleteButton(index) {
    if (confirm('Delete this button?')) {
        buttons.splice(index, 1);
        saveData();
        renderButtons();
    }
}
