# LUNE - Period Calendar

A privacy-first, open-source menstrual cycle tracker built with Kotlin Multiplatform and Compose Multiplatform. All data stays on your device. No account, no cloud, no network, no analytics, no ads.

**Made By Chancecmh1019**

---

## Table of Contents

- [Why LUNE Exists](#why-lune-exists)
- [Core Philosophy](#core-philosophy)
- [Key Features](#key-features)
- [Technical Specifications](#technical-specifications)
- [Architecture](#architecture)
- [Privacy & Security](#privacy--security)

---

## Why LUNE Exists

Most period tracking apps have transformed from simple utilities into data collection platforms. They require accounts, upload sensitive health information to remote servers, include advertising networks, and hide essential features behind paywalls. The user experience suffers as monetization takes priority over functionality.

LUNE represents a deliberate return to fundamentals: a menstrual health tracker that prioritizes user privacy, data ownership, and functionality without compromise. No business model means no pressure to collect data, display ads, or restrict features. What remains is a tool designed solely to be useful, then step out of the way.

This project demonstrates that personal health tracking applications can be built with complete privacy preservation while maintaining professional-grade features and user experience.

---

## Core Philosophy

### Privacy by Design
LUNE operates entirely on your device. No data ever leaves your phone unless you explicitly choose to export it through features you control. This is not a privacy policy promise - it is architectural reality. The application contains no network code for transmitting user data.

### Data Ownership
Your menstrual health information belongs exclusively to you. LUNE provides robust export capabilities in industry-standard formats so you can share data with healthcare providers, migrate to other platforms, or archive for personal records - all at your discretion.

### Medical Transparency
LUNE is a tracking tool, not a medical device. Period predictions use statistical algorithms based on your historical patterns and should not replace professional medical consultation for health decisions, contraception, or family planning.

### Accessibility & Inclusivity
The application supports multiple health profiles including regular cycles, PCOS, endometriosis, hormonal therapy, perimenopause, and other conditions affecting menstrual regularity. This ensures usefulness across diverse health situations.

---

## Key Features

### Comprehensive Cycle Tracking

**Period Logging**
- One-tap period tracking with "Period Arrived" and "Period Gone" buttons
- Estimated end date based on your historical average until you confirm actual end
- Backfill historical periods by selecting date ranges on the calendar
- In-calendar editing to add, delete, extend, or shorten periods directly

**Intelligent Predictions**
- Next 3 cycles predicted based on historical average cycle length
- Anomaly detection filters out cycles shorter than 14 days from prediction calculations
- Smart auto-confirmation for predicted periods 3+ days past expected end
- Medical count-back method for ovulation calculation (ovulation at cycle day minus 14)

**Cycle Phase Tracking**
- Four distinct phases: Menstrual, Follicular, Ovulation, Luteal
- Six-day fertile window centered on ovulation peak
- Per-day phase information with contextual health tips
- Visual indicators showing current phase and upcoming transitions

### Daily Health Logging

**Comprehensive Symptom Tracking**
- Flow intensity (Light, Medium, Heavy)
- Mood tracking with four states
- 14 common symptoms including cramps, headache, fatigue, breast pain
- Bleeding character (Normal, Spotting, Heavy/Abnormal)
- Free-text notes for medications, clinical observations, and personal reminders

**Medical-Grade Detail**
- Medications field for recording prescriptions and dosages
- Clinical notes section for lab results and provider remarks
- Daily record history accessible from calendar detail view

### Home Screen Interface

**Three Viewing Modes**
1. **Overview** - Large circular calendar showing current cycle at a glance with phase indicator
2. **Detail Calendar** - Month-by-month swipeable view with color-coded period days, predictions, ovulation markers
3. **Statistics** - Bar chart of recent cycles, 6-cycle averages, complete record history

### Health Data Integration

**Apple Health & Google Health Connect Sync**
- Bidirectional synchronization of menstrual period and flow data
- Import existing records from health platforms
- Export LUNE records to health platforms
- Completely optional - opt-in only, disabled by default
- All synchronization occurs locally on device through system APIs
- No health information transmitted to external servers

### Smart Notifications

**Three Reminder Types (All Optional)**
- Period reminder 1-7 days before predicted period
- Ovulation reminder 1-7 days before ovulation peak
- Daily report at customizable time

All notifications require explicit OS-level permission and are scheduled entirely on device. No notification data sent to external servers.

### Professional Report Generation

**Comprehensive Export Capabilities**
- Long-image PNG reports with complete cycle history
- Bilingual report generation (English or Traditional Chinese)
- Includes summary statistics, all records, daily logs
- Native system share sheet for direct sharing with healthcare providers
- JSON backup export for data portability
- PDF generation capability

### User Experience Design

**Onboarding Flow**
- Medical disclaimer and privacy notice
- Health profile selection (Regular, PCOS, Endometriosis, Hormonal Therapy, etc.)
- Optional health data import from Apple Health or Google Health Connect
- Period duration and cycle length configuration
- Current cycle status recording
- Auto-generation of 5 past cycles for immediate prediction availability

**Settings & Customization**
- Display mode (System, Light, Dark)
- Language selection (Auto, English, Traditional Chinese)
- Adjustable period duration (2-10 days)
- Adjustable cycle length (20-45 days)
- Complete data export and clearing capabilities

---

## Technical Specifications

### Platform Support
- **Android**: Minimum SDK 26 (Android 8.0), Target SDK 36
- **iOS**: iOS 14.0 and above
- **Shared Codebase**: 95%+ code sharing between platforms

### Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.2 |
| UI Framework | Compose Multiplatform 1.8 |
| Design System | Material Design 3 Expressive |
| Typography | Noto Serif TC + Noto Serif |
| Navigation | Compose Navigation with type-safe routes |
| Dependency Injection | kotlin-inject + KSP |
| Health Integration | HealthKMP (HealthKit + Health Connect) |
| Local Storage | Jetpack DataStore Preferences |
| Serialization | kotlinx.serialization |
| Date/Time | kotlinx-datetime |
| Build System | Gradle 8.7 with version catalog |

### Architecture Pattern

**Clean Architecture with Domain-Driven Design**
- Domain layer contains business logic and entities
- Infrastructure layer handles platform-specific implementations
- UI layer uses unidirectional data flow with Compose state management
- Dependency injection with compile-time verification

```
composeApp/
├── commonMain/
│   ├── kotlin/com/lune/app/
│   │   ├── App.kt                      # Root composable
│   │   ├── di/                         # Dependency injection
│   │   ├── domain/
│   │   │   ├── menstrual/              # Cycle calculations, predictions
│   │   │   ├── health/                 # Health data sync
│   │   │   ├── notifications/          # Reminder scheduling
│   │   │   ├── settings/               # User preferences
│   │   │   └── export/                 # Report generation
│   │   ├── infrastructure/
│   │   │   └── persistence/            # DataStore repositories
│   │   └── ui/
│   │       ├── theme/                  # Material 3 theming
│   │       ├── components/             # Reusable UI components
│   │       ├── navigation/             # Type-safe routes
│   │       └── pages/                  # Screen composables
│   └── resources/                      # Localization strings
├── androidMain/                        # Android-specific code
└── iosMain/                            # iOS-specific code
```

### Data Model

**Core Entities**
- `MenstrualRecord` - Period start/end dates with intensity
- `DailyNote` - Per-day logs (flow, mood, symptoms, notes)
- `PredictedCycle` - Future cycle predictions
- `CyclePhase` - Current phase calculations
- `UserStatus` - Health profile configuration

**Business Logic**
- Cycle length calculation from historical records
- Ovulation date prediction using count-back method
- Anomaly detection (cycles under 14 days)
- Auto-confirmation of overdue predictions
- Phase calculation based on cycle day

---

## Privacy & Security

### Data Storage
All data stored locally using Android DataStore and iOS local storage with system-level encryption. Access restricted to LUNE application and requires device authentication.

### No Data Collection
- No user accounts or authentication
- No personal identifiers collected
- No device identifiers or IP addresses logged
- No analytics or crash reporting
- No advertising networks or tracking SDKs
- No network requests containing user data

### Health Data Handling
- Optional health platform integration is opt-in only
- Synchronization occurs entirely on device through system APIs
- Health permissions managed exclusively by Apple Health or Google Health Connect
- Users control which specific data types are accessible
- Permissions revocable at any time through system settings

### Export & Sharing
- Export features generate files locally on device
- Native system share sheets used for file distribution
- No exported data passes through LUNE servers
- Users control export destinations completely

### Regulatory Compliance
- GDPR compliant (no personal data processing on external systems)
- CCPA compliant (no sale of personal information)
- HIPAA principles followed (health information under user control)
- Google Play Data Safety requirements met
- Transparent privacy policy published

---

**Last Updated**: June 25, 2026
**Version**: v0.0.1
**Developer**: Chancecmh1019