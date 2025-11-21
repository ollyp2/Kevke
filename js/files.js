// FILE MANAGEMENT
function renderFiles() {
    var filesDisplay = document.getElementById('filesDisplay');
    filesDisplay.innerHTML = '';
    if (files.length === 0) return;
    var filesList = document.createElement('div');
    filesList.className = 'files-list';
    files.forEach(function(file, index) {
        var wrapper = document.createElement('div');
        wrapper.className = 'file-item-container';
        var fileItem = document.createElement('div');
        fileItem.className = 'file-item';
        var icon = file.type === 'application/pdf' ? '[PDF]' : '[IMG]';
        fileItem.textContent = icon + ' ' + file.name;
        fileItem.onclick = function() { if (!isEditMode) openFile(file); };
        var deleteBtn = document.createElement('button');
        deleteBtn.className = 'delete-btn';
        deleteBtn.innerHTML = '&times;';
        deleteBtn.onclick = function(e) {
            e.stopPropagation();
            deleteFile(index);
        };
        wrapper.appendChild(fileItem);
        wrapper.appendChild(deleteBtn);
        filesList.appendChild(wrapper);
    });
    filesDisplay.appendChild(filesList);
}

function openFile(file) {
    var newWindow = window.open();
    if (file.type === 'application/pdf') {
        newWindow.document.write('<iframe width="100%" height="100%" src="' + file.data + '"></iframe>');
    } else {
        newWindow.document.write('<img src="' + file.data + '" style="max-width:100%;"/>');
    }
}

function deleteFile(index) {
    if (confirm('Delete this file?')) {
        files.splice(index, 1);
        saveData();
        renderFiles();
    }
}

function handleFiles(fileList) {
    Array.from(fileList).forEach(function(file) {
        if (file.type === 'application/pdf' || file.type === 'image/png' || file.type === 'image/jpeg' || file.type === 'image/jpg') {
            var reader = new FileReader();
            reader.onload = function(e) {
                files.push({ name: file.name, data: e.target.result, type: file.type });
                saveData();
                renderFiles();
            };
            reader.readAsDataURL(file);
        }
    });
}

function setupDragAndDrop() {
    var uploadArea = document.getElementById('uploadArea');
    ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(function(eventName) {
        uploadArea.addEventListener(eventName, function(e) {
            e.preventDefault();
            e.stopPropagation();
        }, false);
    });
    ['dragenter', 'dragover'].forEach(function(eventName) {
        uploadArea.addEventListener(eventName, function() {
            uploadArea.classList.add('drag-over');
        }, false);
    });
    ['dragleave', 'drop'].forEach(function(eventName) {
        uploadArea.addEventListener(eventName, function() {
            uploadArea.classList.remove('drag-over');
        }, false);
    });
    uploadArea.addEventListener('drop', function(e) {
        handleFiles(e.dataTransfer.files);
    }, false);
}

function toggleUpload() {
    var section = document.getElementById('uploadSection');
    var arrow = document.getElementById('uploadArrow');
    section.classList.toggle('open');
    arrow.classList.toggle('up');
}
