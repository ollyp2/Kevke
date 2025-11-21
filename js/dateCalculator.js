// DATE CALCULATOR FUNCTIONS
function openDateCalcModal() {
    showModal('Datumsrechner',
        '<div class="form-group"><label class="form-label">Zieldatum</label>' +
        '<input type="date" class="form-input" id="targetDate"></div>' +
        '<div id="dateCalcResults"></div>' +
        '<div class="modal-actions">' +
        '<button class="action-button save-button" id="calcDateBtn">Berechnen</button>' +
        '<button class="action-button cancel-button" onclick="closeModal()">Schließen</button>' +
        '</div>'
    );
    document.getElementById('calcDateBtn').onclick = calculateDateDifference;
    setTimeout(function() { document.getElementById('targetDate').focus(); }, 100);
}

function calculateDateDifference() {
    var targetDateInput = document.getElementById('targetDate').value;
    if (!targetDateInput) {
        alert('Bitte geben Sie ein Zieldatum ein');
        return;
    }

    var now = new Date();
    now.setHours(0, 0, 0, 0);
    var target = new Date(targetDateInput);
    target.setHours(0, 0, 0, 0);

    var diffTime = target - now;
    var diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    var diffWeeks = Math.floor(Math.abs(diffDays) / 7);

    var isPast = diffTime < 0;
    var startDate = isPast ? target : now;
    var endDate = isPast ? now : target;

    // Calculate years, months, weeks, days
    var years = 0;
    var months = 0;
    var tempDate = new Date(startDate);

    // Count full years
    while (tempDate.getFullYear() < endDate.getFullYear() ||
           (tempDate.getFullYear() === endDate.getFullYear() &&
            (tempDate.getMonth() < endDate.getMonth() ||
             (tempDate.getMonth() === endDate.getMonth() && tempDate.getDate() <= endDate.getDate())))) {
        var nextYear = new Date(tempDate);
        nextYear.setFullYear(nextYear.getFullYear() + 1);
        if (nextYear <= endDate) {
            years++;
            tempDate = nextYear;
        } else {
            break;
        }
    }

    // Count remaining months
    while (tempDate.getMonth() < endDate.getMonth() ||
           (tempDate.getMonth() === endDate.getMonth() && tempDate.getDate() <= endDate.getDate()) ||
           (tempDate.getMonth() > endDate.getMonth() && tempDate < endDate)) {
        var nextMonth = new Date(tempDate);
        nextMonth.setMonth(nextMonth.getMonth() + 1);
        if (nextMonth <= endDate) {
            months++;
            tempDate = nextMonth;
        } else {
            break;
        }
    }

    // Calculate remaining days and weeks
    var remainingDays = Math.ceil((endDate - tempDate) / (1000 * 60 * 60 * 24));
    var remainingWeeks = Math.floor(remainingDays / 7);
    remainingDays = remainingDays % 7;

    var prefix = isPast ? 'vor ' : 'in ';
    var absDays = Math.abs(diffDays);
    var absWeeks = diffWeeks;

    var resultsHtml = '<div class="date-calc-result">';
    resultsHtml += '<div class="date-calc-row"><span class="date-calc-label">Tage</span><span class="date-calc-value">' + prefix + absDays + ' Tagen</span></div>';
    resultsHtml += '<div class="date-calc-row"><span class="date-calc-label">Wochen</span><span class="date-calc-value">' + prefix + absWeeks + ' Wochen</span></div>';

    // Build combined display with years
    var combinedParts = [];
    if (years > 0) combinedParts.push(years + ' Jahr' + (years !== 1 ? 'e' : ''));
    if (months > 0) combinedParts.push(months + ' Monat' + (months !== 1 ? 'e' : ''));
    if (remainingWeeks > 0) combinedParts.push(remainingWeeks + ' Woche' + (remainingWeeks !== 1 ? 'n' : ''));
    if (remainingDays > 0) combinedParts.push(remainingDays + ' Tag' + (remainingDays !== 1 ? 'e' : ''));

    var combinedText = combinedParts.length > 0 ? prefix + combinedParts.join(', ') : 'Heute';

    resultsHtml += '<div class="date-calc-row"><span class="date-calc-label">Kombiniert</span><span class="date-calc-value">' + combinedText + '</span></div>';
    resultsHtml += '</div>';

    document.getElementById('dateCalcResults').innerHTML = resultsHtml;
}
