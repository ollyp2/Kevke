/**
 * CALCULATOR.JS
 * Calculator widget with keyboard support
 *
 * Dependencies: ui.js (for modal closing)
 * Used by: main.js
 */

var calcActive = false;
var calcCurrentValue = '0';
var calcPreviousValue = '';
var calcOperation = null;
var calcNewNumber = true;
var calcHistory = [];
var calcHistoryVisible = false;

function toggleCalculator() {
    calcActive = !calcActive;
    var calc = document.getElementById('calculator');
    var toggle = document.getElementById('calcToggle');
    if (calcActive) {
        calc.classList.add('active');
        toggle.classList.add('active');
    } else {
        calc.classList.remove('active');
        toggle.classList.remove('active');
    }
}

function updateCalcDisplay() {
    var displayText = calcCurrentValue;
    if (calcOperation && calcPreviousValue) {
        var opSymbol = calcOperation === '*' ? '×' : calcOperation === '-' ? '−' : calcOperation;
        displayText = calcPreviousValue + ' ' + opSymbol + ' ' + (calcNewNumber ? '' : calcCurrentValue);
    }
    document.getElementById('calcDisplay').textContent = displayText;
}

function calcClear() {
    calcCurrentValue = '0';
    calcPreviousValue = '';
    calcOperation = null;
    calcNewNumber = true;
    updateCalcDisplay();
}

function calcBackspace() {
    if (calcCurrentValue.length > 1) {
        calcCurrentValue = calcCurrentValue.slice(0, -1);
    } else {
        calcCurrentValue = '0';
    }
    updateCalcDisplay();
}

function calcInputNumber(num) {
    if (calcNewNumber) {
        calcCurrentValue = num;
        calcNewNumber = false;
    } else {
        if (calcCurrentValue === '0' && num !== '.') {
            calcCurrentValue = num;
        } else {
            if (num === '.' && calcCurrentValue.includes('.')) return;
            calcCurrentValue += num;
        }
    }
    updateCalcDisplay();
}

function calcInputOperator(op) {
    if (calcOperation && !calcNewNumber) {
        calcExecute();
    }
    calcPreviousValue = calcCurrentValue;
    calcOperation = op;
    calcNewNumber = true;
}

function calcExecute() {
    if (!calcOperation || calcPreviousValue === '') return;
    var prev = parseFloat(calcPreviousValue);
    var curr = parseFloat(calcCurrentValue);
    var result = 0;
    var opSymbol = calcOperation === '*' ? '×' : calcOperation === '-' ? '−' : calcOperation;
    switch (calcOperation) {
        case '+': result = prev + curr; break;
        case '-': result = prev - curr; break;
        case '*': result = prev * curr; break;
        case '/': result = curr !== 0 ? prev / curr : 0; break;
    }

    // Add to history
    var historyEntry = calcPreviousValue + ' ' + opSymbol + ' ' + calcCurrentValue + ' = ' + result;
    calcHistory.unshift(historyEntry);
    if (calcHistory.length > 10) calcHistory.pop(); // Keep only last 10
    updateCalcHistory();

    calcCurrentValue = result.toString();
    calcPreviousValue = '';
    calcOperation = null;
    calcNewNumber = true;
    updateCalcDisplay();
}

function toggleCalcHistory() {
    calcHistoryVisible = !calcHistoryVisible;
    var historyEl = document.getElementById('calcHistory');
    if (calcHistoryVisible) {
        historyEl.style.display = 'block';
    } else {
        historyEl.style.display = 'none';
    }
}

function updateCalcHistory() {
    var historyEl = document.getElementById('calcHistory');
    if (!historyEl) return;
    var html = '';
    calcHistory.forEach(function(entry) {
        html += '<div class="calc-history-item">' + entry + '</div>';
    });
    historyEl.innerHTML = html || '<div class="calc-history-empty">Keine Rechnungen</div>';
}

function handleCalcButton(btn) {
    var text = btn.textContent;
    if (text === 'C') {
        calcClear();
    } else if (text === '⌫') {
        calcBackspace();
    } else if (text === '=') {
        calcExecute();
    } else if (['+', '−', '×', '/'].includes(text)) {
        var op = text === '×' ? '*' : text === '−' ? '-' : text;
        calcInputOperator(op);
    } else {
        calcInputNumber(text);
    }
}

function setupCalculator() {
    var calcButtons = document.querySelectorAll('.calc-btn');
    calcButtons.forEach(function(btn) {
        btn.addEventListener('click', function() {
            handleCalcButton(btn);
        });
    });

    document.addEventListener('keydown', function(e) {
        if (!calcActive) return;

        var key = e.key;
        if (key >= '0' && key <= '9') {
            calcInputNumber(key);
            e.preventDefault();
        } else if (key === '.') {
            calcInputNumber('.');
            e.preventDefault();
        } else if (key === '+' || key === '-' || key === '*' || key === '/') {
            calcInputOperator(key);
            e.preventDefault();
        } else if (key === 'Enter' || key === '=') {
            calcExecute();
            e.preventDefault();
        } else if (key === 'Escape' || key === 'c' || key === 'C') {
            calcClear();
            e.preventDefault();
        } else if (key === 'Backspace') {
            calcBackspace();
            e.preventDefault();
        }
    });
}
