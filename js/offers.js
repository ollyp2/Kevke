/**
 * OFFERS.JS
 * Offers/Angebote tracking with status indicators (active, expiring, expired, future)
 *
 * Dependencies: ui.js, storage.js, config.js
 * Used by: main.js
 */

function getOfferStatus(offer) {
    var now = new Date();
    now.setHours(0, 0, 0, 0);
    var endDate = new Date(offer.end);
    endDate.setHours(0, 0, 0, 0);
    var startDate = new Date(offer.start);
    startDate.setHours(0, 0, 0, 0);

    // Check if offer hasn't started yet (future offer)
    if (now < startDate) {
        return { status: 'future', text: 'Angebot bald verfügbar' };
    }

    var diffTime = endDate - now;
    var diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays < 0) {
        return { status: 'expired', text: 'Angebot ist abgelaufen' };
    } else if (diffDays <= CONFIG.OFFERS.EXPIRING_DAYS_THRESHOLD) {
        return { status: 'expiring', text: 'Angebot läuft innerhalb der nächsten ' + CONFIG.OFFERS.EXPIRING_DAYS_THRESHOLD + ' Tage ab' };
    } else {
        return { status: 'active', text: 'Aktives Angebot' };
    }
}

function renderOffers() {
    var container = document.getElementById('offersTableContainer');
    var addBtn = document.getElementById('addOfferBtn');

    if (offers.length === 0) {
        container.innerHTML = '<div class="offers-empty">Keine Angebote vorhanden. Klicken Sie auf "+ Angebot" um ein neues Angebot hinzuzufügen.</div>';
        addBtn.classList.add('show-always');
        return;
    }

    addBtn.classList.remove('show-always');

    var tableHtml = '<table class="offers-table"><thead><tr><th>Status</th><th>Angebot</th><th>Beginn</th><th>Ende</th></tr></thead><tbody>';

    offers.forEach(function(offer, index) {
        var isExpanded = expandedOffers.indexOf(index) !== -1;
        var formattedStart = formatDate(offer.start);
        var formattedEnd = formatDate(offer.end);
        var status = getOfferStatus(offer);

        var rowClass = 'offer-row';
        if (status.status === 'expired') {
            rowClass += ' expired';
        }

        tableHtml += '<tr class="' + rowClass + '" onclick="toggleOfferDescription(' + index + ')">';
        tableHtml += '<td class="offer-status"><span class="status-dot ' + status.status + '" title="' + status.text + '"></span></td>';
        tableHtml += '<td><span class="offer-title">' + offer.title + '</span>';
        if (isEditMode) {
            tableHtml += '<div class="offer-actions" onclick="event.stopPropagation()">';
            tableHtml += '<button class="offer-edit-btn" onclick="editOffer(' + index + ')">Bearbeiten</button>';
            tableHtml += '<button class="offer-delete-btn" onclick="deleteOffer(' + index + ')">Löschen</button>';
            tableHtml += '</div>';
        }
        tableHtml += '</td>';
        tableHtml += '<td>' + formattedStart + '</td>';
        tableHtml += '<td>' + formattedEnd + '</td>';
        tableHtml += '</tr>';

        if (offer.description) {
            tableHtml += '<tr class="offer-description-row' + (isExpanded ? ' expanded' : '') + '">';
            tableHtml += '<td colspan="4" class="offer-description-cell">' + offer.description + '</td>';
            tableHtml += '</tr>';
        }
    });

    tableHtml += '</tbody></table>';
    container.innerHTML = tableHtml;
}

function formatDate(dateString) {
    if (!dateString) return '';
    var date = new Date(dateString);
    var day = date.getDate().toString().padStart(2, '0');
    var month = (date.getMonth() + 1).toString().padStart(2, '0');
    var year = date.getFullYear();
    return day + '.' + month + '.' + year;
}

function toggleOfferDescription(index) {
    var expandedIndex = expandedOffers.indexOf(index);
    if (expandedIndex === -1) {
        expandedOffers.push(index);
    } else {
        expandedOffers.splice(expandedIndex, 1);
    }
    renderOffers();
}

function openAddOfferModal() {
    showModal('Neues Angebot',
        '<div class="form-group"><label class="form-label">Titel (Angebot)</label>' +
        '<input type="text" class="form-input" id="offerTitle" placeholder="Angebots-Titel"></div>' +
        '<div class="form-group"><label class="form-label">Beginn</label>' +
        '<input type="date" class="form-input" id="offerStart"></div>' +
        '<div class="form-group"><label class="form-label">Ende</label>' +
        '<input type="date" class="form-input" id="offerEnd"></div>' +
        '<div class="form-group"><label class="form-label">Angebotsbeschreibung</label>' +
        '<textarea class="form-textarea" id="offerDescription" placeholder="Beschreibung des Angebots"></textarea></div>' +
        '<div class="modal-actions">' +
        '<button class="action-button save-button" id="saveOfferBtn">Speichern</button>' +
        '<button class="action-button cancel-button" onclick="closeModal()">Abbrechen</button>' +
        '</div>'
    );
    document.getElementById('saveOfferBtn').onclick = saveNewOffer;
    setTimeout(function() { document.getElementById('offerTitle').focus(); }, 100);
}

function saveNewOffer() {
    var title = document.getElementById('offerTitle').value.trim();
    var start = document.getElementById('offerStart').value;
    var end = document.getElementById('offerEnd').value;
    var description = document.getElementById('offerDescription').value.trim();

    if (!title) {
        alert('Bitte geben Sie einen Titel ein');
        return;
    }
    if (!start) {
        alert('Bitte geben Sie ein Beginndatum ein');
        return;
    }
    if (!end) {
        alert('Bitte geben Sie ein Enddatum ein');
        return;
    }

    offers.push({
        title: title,
        start: start,
        end: end,
        description: description
    });

    saveData();
    renderOffers();
    closeModal();
}

function editOffer(index) {
    var offer = offers[index];
    showModal('Angebot bearbeiten',
        '<div class="form-group"><label class="form-label">Titel (Angebot)</label>' +
        '<input type="text" class="form-input" id="offerTitle" value="' + offer.title + '"></div>' +
        '<div class="form-group"><label class="form-label">Beginn</label>' +
        '<input type="date" class="form-input" id="offerStart" value="' + offer.start + '"></div>' +
        '<div class="form-group"><label class="form-label">Ende</label>' +
        '<input type="date" class="form-input" id="offerEnd" value="' + offer.end + '"></div>' +
        '<div class="form-group"><label class="form-label">Angebotsbeschreibung</label>' +
        '<textarea class="form-textarea" id="offerDescription">' + (offer.description || '') + '</textarea></div>' +
        '<div class="modal-actions">' +
        '<button class="action-button save-button" id="updateOfferBtn">Speichern</button>' +
        '<button class="action-button cancel-button" onclick="closeModal()">Abbrechen</button>' +
        '</div>'
    );
    document.getElementById('updateOfferBtn').onclick = function() { updateOffer(index); };
    setTimeout(function() { document.getElementById('offerTitle').focus(); }, 100);
}

function updateOffer(index) {
    var title = document.getElementById('offerTitle').value.trim();
    var start = document.getElementById('offerStart').value;
    var end = document.getElementById('offerEnd').value;
    var description = document.getElementById('offerDescription').value.trim();

    if (!title) {
        alert('Bitte geben Sie einen Titel ein');
        return;
    }
    if (!start) {
        alert('Bitte geben Sie ein Beginndatum ein');
        return;
    }
    if (!end) {
        alert('Bitte geben Sie ein Enddatum ein');
        return;
    }

    offers[index] = {
        title: title,
        start: start,
        end: end,
        description: description
    };

    saveData();
    renderOffers();
    closeModal();
}

function deleteOffer(index) {
    if (confirm('Möchten Sie dieses Angebot wirklich löschen?')) {
        offers.splice(index, 1);
        var expandedIndex = expandedOffers.indexOf(index);
        if (expandedIndex !== -1) {
            expandedOffers.splice(expandedIndex, 1);
        }
        saveData();
        renderOffers();
    }
}
