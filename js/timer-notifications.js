/**
 * TIMER-NOTIFICATIONS.JS
 * Handles browser notifications for timers
 *
 * Dependencies: None
 * Used by: timer-core.js
 */

var notificationAsked = false;

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
