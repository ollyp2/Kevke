// TIMER FUNCTIONS
var activeTimers = [];
var expiredTimers = [];
var timerIntervals = {};
var timerIdCounter = 0;
var lastTimerConfig = null;
var notificationAsked = false;
var timeVisible = true;

function requestNotification() {
    if (notificationAsked) return;
    if ("Notification" in window && Notification.permission === "default") {
        notificationAsked = true;
        Notification.requestPermission().then(function(permission) {
            if (permission === "granted") {
                console.log('Notifications enabled!');
            }
        });
    }
}

function showNotification(title, body) {
    console.log('Trying to show notification:', title, body);
    console.log('Notification permission:', Notification.permission);

    if ("Notification" in window) {
        if (Notification.permission === "granted") {
            try {
                var notification = new Notification(title, {
                    body: body,
                    requireInteraction: true,
                    tag: 'timer-' + Date.now()
                });
                console.log('Notification created successfully');
            } catch (e) {
                console.error('Notification error:', e);
            }
        } else if (Notification.permission === "default") {
            console.log('Notification permission not granted yet');
        } else {
            console.log('Notification permission denied');
        }
    } else {
        console.log('Notifications not supported in this browser');
    }
}

function updateClock() {
    if (!timeVisible) return;
    var now = new Date();
    document.getElementById('dashTime').textContent = now.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    });
    document.getElementById('dashDate').textContent = now.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });
}

function toggleTimeVisibility() {
    timeVisible = !timeVisible;
    var timeEl = document.getElementById('dashTime');
    if (timeVisible) {
        timeEl.classList.remove('hidden');
        updateClock();
    } else {
        timeEl.classList.add('hidden');
        timeEl.textContent = '--:--:--';
    }
}

function updateActiveTimersDisplay() {
    var display = document.getElementById('activeTimersDisplay');
    var listContainer = document.getElementById('activeTimersList');
    var badge = document.getElementById('timerBadge');

    if (activeTimers.length === 0) {
        display.classList.remove('visible');
        badge.classList.remove('active');
        return;
    }

    display.classList.add('visible');
    badge.classList.add('active');
    badge.textContent = activeTimers.length;

    listContainer.innerHTML = '';
    activeTimers.forEach(function(timer) {
        var itemDiv = document.createElement('div');
        itemDiv.className = 'active-timer-item';
        itemDiv.id = 'timer-item-' + timer.id;

        var infoDiv = document.createElement('div');
        infoDiv.className = 'active-timer-info';

        var typeDiv = document.createElement('div');
        typeDiv.className = 'active-timer-type';
        typeDiv.textContent = timer.title;
        if (timer.description) {
            typeDiv.title = timer.description;
        }

        var countdownDiv = document.createElement('div');
        countdownDiv.className = 'active-timer-countdown';
        countdownDiv.id = 'timer-countdown-' + timer.id;
        countdownDiv.textContent = '00:00:00';

        infoDiv.appendChild(typeDiv);
        infoDiv.appendChild(countdownDiv);

        var stopBtn = document.createElement('button');
        stopBtn.className = 'active-timer-stop';
        stopBtn.textContent = 'Stop';
        stopBtn.onclick = function() { stopTimer(timer.id); };

        itemDiv.appendChild(infoDiv);
        itemDiv.appendChild(stopBtn);
        listContainer.appendChild(itemDiv);
    });
}

function updateExpiredTimersDisplay() {
    var modal = document.getElementById('expiredTimersModal');
    var listContainer = document.getElementById('expiredTimersList');

    if (expiredTimers.length === 0) {
        modal.classList.remove('visible');
        return;
    }

    modal.classList.add('visible');
    listContainer.innerHTML = '';

    expiredTimers.forEach(function(timer, index) {
        var itemDiv = document.createElement('div');
        itemDiv.className = 'expired-timer-item';

        var titleDiv = document.createElement('div');
        titleDiv.className = 'expired-timer-title';
        titleDiv.textContent = timer.title;

        itemDiv.appendChild(titleDiv);

        if (timer.description) {
            var descDiv = document.createElement('div');
            descDiv.className = 'expired-timer-description';
            descDiv.textContent = timer.description;
            itemDiv.appendChild(descDiv);
        }

        var actionsDiv = document.createElement('div');
        actionsDiv.className = 'expired-timer-actions';

        var restartBtn = document.createElement('button');
        restartBtn.className = 'expired-timer-btn restart-expired-btn';
        restartBtn.textContent = 'Neu starten';
        restartBtn.onclick = function() { restartExpiredTimer(index); };

        var dismissBtn = document.createElement('button');
        dismissBtn.className = 'expired-timer-btn dismiss-expired-btn';
        dismissBtn.textContent = 'Schließen';
        dismissBtn.onclick = function() { dismissExpiredTimer(index); };

        actionsDiv.appendChild(restartBtn);
        actionsDiv.appendChild(dismissBtn);
        itemDiv.appendChild(actionsDiv);

        listContainer.appendChild(itemDiv);
    });
}

function restartExpiredTimer(index) {
    var timer = expiredTimers[index];
    if (timer.type === 'countdown') {
        var h = Math.floor(timer.duration / 3600);
        var m = Math.floor((timer.duration % 3600) / 60);
        var s = timer.duration % 60;
        startTimer('countdown', h + ':' + m + ':' + s, timer.title, timer.description);
    } else {
        startTimer('alarm', timer.target, timer.title, timer.description);
    }
    dismissExpiredTimer(index);
}

function dismissExpiredTimer(index) {
    expiredTimers.splice(index, 1);
    updateExpiredTimersDisplay();
}

function dismissAllExpiredTimers() {
    expiredTimers = [];
    updateExpiredTimersDisplay();
}

function updateTimerCountdown(timerId) {
    var timer = activeTimers.find(function(t) { return t.id === timerId; });
    if (!timer) return;

    var now = new Date();
    var countdownEl = document.getElementById('timer-countdown-' + timerId);
    if (!countdownEl) return;

    if (timer.type === 'countdown') {
        var elapsed = Math.floor((now - timer.start) / 1000);
        var remaining = timer.duration - elapsed;

        if (remaining <= 0) {
            var notifBody = timer.description || 'Dein Countdown-Timer ist abgelaufen.';
            showNotification(timer.title, notifBody);

            expiredTimers.push(timer);
            stopTimer(timerId);
            updateExpiredTimersDisplay();
            return;
        }

        var h = Math.floor(remaining / 3600);
        var m = Math.floor((remaining % 3600) / 60);
        var s = remaining % 60;
        countdownEl.textContent = h.toString().padStart(2, '0') + ':' +
                                 m.toString().padStart(2, '0') + ':' +
                                 s.toString().padStart(2, '0');
    } else if (timer.type === 'alarm') {
        var target = new Date(timer.target);
        if (now >= target) {
            var notifBody = timer.description || 'Deine Alarmzeit wurde erreicht.';
            showNotification(timer.title, notifBody);

            expiredTimers.push(timer);
            stopTimer(timerId);
            updateExpiredTimersDisplay();
            return;
        }

        var diff = Math.floor((target - now) / 1000);
        var h = Math.floor(diff / 3600);
        var m = Math.floor((diff % 3600) / 60);
        var s = diff % 60;
        countdownEl.textContent = h.toString().padStart(2, '0') + ':' +
                                 m.toString().padStart(2, '0') + ':' +
                                 s.toString().padStart(2, '0');
    }
}

function startTimer(type, value, title, description) {
    var timerId = timerIdCounter++;
    var timer = {
        id: timerId,
        type: type,
        title: title || (type === 'countdown' ? 'Countdown' : 'Alarm'),
        description: description || ''
    };

    if (type === 'countdown') {
        var parts = value.split(':');
        var hours = parseInt(parts[0]) || 0;
        var minutes = parseInt(parts[1]) || 0;
        var seconds = parseInt(parts[2]) || 0;
        timer.duration = hours * 3600 + minutes * 60 + seconds;
        timer.start = new Date();
    } else {
        timer.target = value;
    }

    activeTimers.push(timer);

    timerIntervals[timerId] = setInterval(function() {
        updateTimerCountdown(timerId);
    }, 1000);

    updateActiveTimersDisplay();
    updateTimerCountdown(timerId);

    var notifTitle = timer.title;
    var notifBody = timer.description ? timer.description : (type === 'countdown' ? 'Countdown-Timer gestartet' : 'Alarm gesetzt');
    showNotification(notifTitle, notifBody);
    requestNotification();

    lastTimerConfig = { type: type, value: value, title: title, description: description };
}

function stopTimer(timerId) {
    if (timerIntervals[timerId]) {
        clearInterval(timerIntervals[timerId]);
        delete timerIntervals[timerId];
    }

    activeTimers = activeTimers.filter(function(t) { return t.id !== timerId; });
    updateActiveTimersDisplay();
}

function openTimerModal() {
    var activeTimersHtml = '';
    if (activeTimers.length > 0) {
        activeTimersHtml = '<div style="background: rgba(60, 180, 60, 0.2); border: 1px solid rgba(60, 180, 60, 0.5); border-radius: 8px; padding: 1rem; margin-bottom: 1rem; text-align: center;">';
        activeTimersHtml += '<div style="color: #5cb85c; font-weight: 600; margin-bottom: 0.5rem;">' + activeTimers.length + ' Timer aktiv</div>';
        activeTimersHtml += '<div style="color: #aaa; font-size: 0.9rem;">Timer werden unten rechts angezeigt</div>';
        activeTimersHtml += '</div>';
    }

    showModal('Timer & Alarm',
        activeTimersHtml +
        '<div class="timer-tabs">' +
        '<div class="timer-tab active" onclick="switchTimerTab(\'countdown\')" id="countdownTab">Countdown</div>' +
        '<div class="timer-tab" onclick="switchTimerTab(\'alarm\')" id="alarmTab">Uhrzeit</div>' +
        '</div>' +
        '<div id="timerContent">' +
        '<div id="countdownContent">' +
        '<div class="form-group"><label class="form-label">Titel</label>' +
        '<input type="text" class="form-input" id="timerTitle" placeholder="z.B. Pizza im Ofen" value="Countdown"></div>' +
        '<div class="form-group"><label class="form-label">Beschreibung (optional)</label>' +
        '<textarea class="form-textarea" id="timerDescription" placeholder="z.B. Bei 200°C backen" style="min-height: 80px;"></textarea></div>' +
        '<div class="form-group"><label class="form-label">Dauer (HH:MM:SS)</label>' +
        '<div class="timer-inputs">' +
        '<div class="timer-input-group"><input type="number" class="form-input" id="hoursInput" placeholder="HH" min="0" max="23" value="0"></div>' +
        '<div class="timer-input-group"><input type="number" class="form-input" id="minutesInput" placeholder="MM" min="0" max="59" value="5"></div>' +
        '<div class="timer-input-group"><input type="number" class="form-input" id="secondsInput" placeholder="SS" min="0" max="59" value="0"></div>' +
        '</div></div></div>' +
        '<div id="alarmContent" style="display:none">' +
        '<div class="form-group"><label class="form-label">Titel</label>' +
        '<input type="text" class="form-input" id="alarmTitle" placeholder="z.B. Meeting" value="Alarm"></div>' +
        '<div class="form-group"><label class="form-label">Beschreibung (optional)</label>' +
        '<textarea class="form-textarea" id="alarmDescription" placeholder="z.B. Raum 3.14" style="min-height: 80px;"></textarea></div>' +
        '<div class="form-group"><label class="form-label">Uhrzeit</label>' +
        '<input type="time" class="form-input" id="alarmTimeInput"></div>' +
        '</div></div>' +
        '<div class="modal-actions">' +
        '<button class="action-button save-button" id="startTimerBtn">Timer starten</button>' +
        '<button class="action-button cancel-button" onclick="closeModal()">Abbrechen</button>' +
        '</div>'
    );

    document.getElementById('startTimerBtn').onclick = function() {
        var activeTab = document.querySelector('.timer-tab.active').id;
        if (activeTab === 'countdownTab') {
            var h = parseInt(document.getElementById('hoursInput').value) || 0;
            var m = parseInt(document.getElementById('minutesInput').value) || 0;
            var s = parseInt(document.getElementById('secondsInput').value) || 0;
            if (h === 0 && m === 0 && s === 0) {
                alert('Bitte setze eine Dauer');
                return;
            }
            var title = document.getElementById('timerTitle').value.trim() || 'Countdown';
            var description = document.getElementById('timerDescription').value.trim();
            startTimer('countdown', h + ':' + m + ':' + s, title, description);
        } else {
            var time = document.getElementById('alarmTimeInput').value;
            if (!time) {
                alert('Bitte setze eine Uhrzeit');
                return;
            }
            var title = document.getElementById('alarmTitle').value.trim() || 'Alarm';
            var description = document.getElementById('alarmDescription').value.trim();
            var today = new Date();
            var timeParts = time.split(':');
            var hours = timeParts[0];
            var minutes = timeParts[1];
            var target = new Date(today.getFullYear(), today.getMonth(), today.getDate(), hours, minutes, 0);
            if (target <= new Date()) {
                target.setDate(target.getDate() + 1);
            }
            startTimer('alarm', target.toISOString(), title, description);
        }
        closeModal();
    };
}

function switchTimerTab(tab) {
    document.querySelectorAll('.timer-tab').forEach(function(t) { t.classList.remove('active'); });
    if (tab === 'countdown') {
        document.getElementById('countdownTab').classList.add('active');
        document.getElementById('countdownContent').style.display = 'block';
        document.getElementById('alarmContent').style.display = 'none';
    } else {
        document.getElementById('alarmTab').classList.add('active');
        document.getElementById('countdownContent').style.display = 'none';
        document.getElementById('alarmContent').style.display = 'block';
    }
}
