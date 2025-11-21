# KevWorkdesk powered by CCOne

A modular, feature-rich productivity workspace with timer, calculator, notes, offers tracking, and file management.

## 📁 Project Structure

```
Kevke/
├── index.html              # Main HTML structure (130 lines)
├── .gitignore              # Git ignore rules
├── css/
│   └── styles.css          # All CSS styles (32KB)
└── js/
    ├── config.js           # Configuration & constants
    ├── storage.js          # Data management & localStorage
    ├── ui.js               # Modal & UI helpers
    ├── calculator.js       # Calculator widget
    ├── dateCalculator.js   # Date difference calculator
    ├── averageCalculator.js# Average calculator (Ø)
    ├── timer-notifications.js # Timer notifications
    ├── timer-core.js       # Timer core logic
    ├── timer-ui.js         # Timer UI & modal
    ├── offers.js           # Offers/Angebote tracking
    ├── notes.js            # Notes management
    ├── buttons.js          # Navigation buttons
    ├── files.js            # File upload & management
    └── main.js             # Initialization & event listeners
```

## ✨ Features

- **Calculator** - Full calculator with keyboard support
- **Date Calculator** - Calculate time differences between dates
- **Average Calculator** - Quick average calculations
- **Timer & Alarm** - Countdown timers and alarms with browser notifications
- **Notes System** - Create, edit, and organize notes with drag-and-drop
- **Offers Tracking** - Track offers with status indicators (active, expiring, future)
- **File Management** - Upload and manage PDF, PNG, JPG files
- **Navigation Buttons** - Customizable quick-access buttons
- **Edit Mode** - Toggle edit mode to manage all elements
- **Persistent Storage** - All data saved in localStorage

## 🔧 Modular Architecture

Each feature is split into its own module for:
- **Better maintainability** - Edit only what you need
- **Easier debugging** - Find issues quickly
- **Cleaner code** - No more searching through 2400+ lines
- **Faster development** - Work on features independently

## 🚀 Quick Start

1. Open `index.html` in your browser
2. Click "Edit Mode" to customize buttons and manage content
3. Start using the features!

## 📝 Making Changes

Need to modify a specific feature? Here's where to look:

| Feature | File | Lines |
|---------|------|-------|
| Calculator logic | `js/calculator.js` | 140 |
| Timer notifications | `js/timer-notifications.js` | 47 |
| Timer core logic | `js/timer-core.js` | 180 |
| Timer UI | `js/timer-ui.js` | 225 |
| Notes system | `js/notes.js` | 157 |
| Offers tracking | `js/offers.js` | 214 |
| Config/Constants | `js/config.js` | 47 |
| Styles | `css/styles.css` | 956 |
| Data storage | `js/storage.js` | 67 |

**Each file has a header comment explaining:**
- What it does
- What it depends on
- What uses it

## 💾 Data Storage

All data is stored in browser localStorage:
- `workdesk_notes` - Note contents
- `workdesk_note_titles` - Note titles
- `workdesk_files` - Uploaded files (base64)
- `workdesk_buttons` - Navigation buttons
- `workdesk_avg_values` - Average calculator values
- `workdesk_offers` - Offers data

## 🎨 Customization

The modular structure makes it easy to:
- Add new features (create a new `.js` module)
- Modify existing features (edit the relevant module)
- Change styles (edit `css/styles.css`)
- Rearrange features (update `index.html`)
