/**
 * TIMER-CORE.JS
 * Core timer logic - start, stop, update, countdown
 *
 * Dependencies: timer-notifications.js, timer-ui.js
 * Used by: timer-ui.js, main.js
 */

var activeTimers = [];
var expiredTimers = [];
var timerIntervals = {};
var timerIdCounter = 0;
var lastTimerConfig = null;
var timeVisible = true;

// Clock functions
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

// Timer countdown logic
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

// Timer start/stop
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
    }, CONFIG.TIMER.UPDATE_INTERVAL_MS);

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

// Expired timer management
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
