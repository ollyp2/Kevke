/**
 * AVERAGECALCULATOR.JS
 * Average calculator (Ø) - calculate average of multiple values
 *
 * Dependencies: ui.js, storage.js
 * Used by: main.js
 */

function calculateAverage() {
    if (avgValues.length === 0) return 0;
    var sum = avgValues.reduce(function(acc, val) { return acc + val; }, 0);
    return sum / avgValues.length;
}

function renderAvgModal() {
    var avg = calculateAverage();
    var resultHtml = avgValues.length > 0 ?
        '<div class="avg-result"><div class="avg-result-label">Durchschnitt</div><div class="avg-result-value">' + avg.toFixed(2) + '</div></div>' : '';

    var valuesHtml = '';
    if (avgValues.length > 0) {
        valuesHtml = '<div class="avg-values-list">';
        avgValues.forEach(function(val, index) {
            valuesHtml += '<div class="avg-value-item"><span class="avg-value-text">' + val + '</span><button class="avg-delete-btn" onclick="deleteAvgValue(' + index + ')">✕</button></div>';
        });
        valuesHtml += '</div>';
    }

    showModal('Durchschnittsrechner (Ø)',
        resultHtml +
        valuesHtml +
        '<div class="form-group"><label class="form-label">Neuer Wert</label>' +
        '<div class="avg-input-row"><input type="number" step="any" class="form-input" id="avgInput" placeholder="Zahl eingeben">' +
        '<button class="action-button save-button" onclick="addAvgValue()" style="flex:0 0 auto; padding:0.8rem 1.5rem">+</button></div></div>' +
        '<div class="modal-actions">' +
        '<button class="action-button delete-button" onclick="clearAvgValues()">Alle löschen</button>' +
        '<button class="action-button cancel-button" onclick="closeModal()">Schließen</button>' +
        '</div>'
    );
    setTimeout(function() {
        var input = document.getElementById('avgInput');
        if (input) {
            input.focus();
            input.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    addAvgValue();
                }
            });
        }
    }, CONFIG.UI.FOCUS_DELAY_MS);
}

function openAvgModal() {
    renderAvgModal();
}

function addAvgValue() {
    var input = document.getElementById('avgInput');
    var value = parseFloat(input.value);
    if (isNaN(value)) {
        alert('Bitte geben Sie eine gültige Zahl ein');
        return;
    }
    avgValues.push(value);
    saveData();
    renderAvgModal();
}

function deleteAvgValue(index) {
    avgValues.splice(index, 1);
    saveData();
    renderAvgModal();
}

function clearAvgValues() {
    if (confirm('Alle Werte löschen?')) {
        avgValues = [];
        saveData();
        renderAvgModal();
    }
}
