# SplitSaathi — Cookie & Local Storage Notice

**Effective Date:** October 29, 2026  
**Last Updated:** October 29, 2026  

---

## 1. Overview
SplitSaathi is an Android native mobile application. SplitSaathi does not use HTTP web cookies or cross-site tracking beacons.

## 2. On-Device Storage Technologies Used
In place of browser cookies, the Android operating system provides sandboxed local storage:

| Storage Type | Purpose | Persistence | Security / Backup Control |
|:---|:---|:---|:---|
| **Android SharedPreferences (`splitsaathi_user_session.xml`)** | Stores active user identifier, profile preferences, and UPI consent. | Persistent until user taps "Delete Account" or clears app storage. | **Strictly excluded** from Google Cloud Auto-Backup via `backup_rules.xml` and `data_extraction_rules.xml`. |
| **Room Database (SQLite: `splitsaathi.db`)** | Encrypted/sandboxed local ledger cache for offline reading and rapid UI responsiveness. | Persistent on device. | Protected by Android app sandbox uid permissions. |

## 3. Third-Party Analytics & Tracking
- **Zero Third-Party Ad Trackers:** SplitSaathi contains no Facebook Pixel, Google AdMob, AppsFlyer, Adjust, or other behavioral advertising SDKs.
- **No Cross-App Tracking:** SplitSaathi does not query the Android Advertising ID (AAID) or track user behaviors across applications.
