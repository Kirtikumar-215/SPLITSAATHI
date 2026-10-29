# SplitSaathi — Privacy Policy

**Effective Date:** October 29, 2026  
**Last Updated:** October 29, 2026  
**Applicability:** SplitSaathi Android Application (India / Global)

---

## 1. Introduction & Overview
SplitSaathi ("we", "us", "our") provides an informal shared expense calculator and ledger application. We operate on a data-minimization philosophy: we do not sell your personal data, conduct behavioral profiling, or operate advertising networks.

## 2. Personal Data We Collect
We collect only the minimal data strictly required to calculate group expenses and facilitate peer-to-peer settlement:
- **Display Name / Full Name:** Self-declared by you during onboarding; used by group members to identify who paid or owes money.
- **Mobile Number (Optional):** Self-declared 10-digit Indian phone number; used to help group members recognize contacts.
- **UPI ID (Optional):** Self-declared Virtual Payment Address (e.g., `user@bank`); shared exclusively with verified members of groups you join who need to settle debts with you.
- **Transaction Records:** Titles, amounts (stored in exact integer paise), split preferences, payment claims, and optional UTR/receipt notes.

**What We Do NOT Collect:**
- No passwords, PINs, or biometric credentials.
- No bank account numbers, IFSC codes, or debit/credit card numbers.
- No background device location or GPS coordinates.
- No contact book scraping or automatic SMS reading.

## 3. Legal Basis & User Consent
In compliance with the **Digital Personal Data Protection (DPDP) Act, 2023 (India)** and the **General Data Protection Regulation (GDPR)**:
- Processing is performed based on explicit user consent and necessary performance of the ledger contract.
- Sharing your UPI ID is gated behind explicit opt-in consent ("I allow SplitSaathi to show my UPI ID to group members who need to pay me"). You can withdraw or toggle this consent anytime in Profile.

## 4. Data Storage & Security
- **Local Device Storage:** Encrypted or sandboxed SQLite via Room Database and Android SharedPreferences. Credentials and session tokens are strictly excluded from automated cloud backups via Android backup rules (`backup_rules.xml` and `data_extraction_rules.xml`).
- **Cloud Backend:** When synchronized to our Supabase PostgreSQL cloud backend, data is secured using Row Level Security (RLS) and stored in EU/India regions with TLS 1.3 in transit and AES-256 at rest.

## 5. Peer-to-Peer Payments Disclaimer
SplitSaathi is **not** a bank, payment aggregator, or Unified Payments Interface (UPI) system participant. When you tap "Pay via UPI", SplitSaathi creates an Android system intent (`upi://pay`) transferring control directly to your installed bank or payment application (e.g., Google Pay, PhonePe, Paytm). SplitSaathi never touches, transmits, or holds your money.

## 6. User Rights & Account Deletion
You retain complete rights over your data:
- **Access & Correction:** Update your name, phone number, and UPI ID anytime directly from the Profile screen.
- **Account Deletion / Erasure:** Tapping "Delete Account & Reset Data" immediately wipes your local session and anonymizes your profile records (`displayName = "Deactivated Member"`, `phone = null`, `upiId = null`). Financial group entries remain recorded in anonymized form to preserve mutual ledger integrity for remaining group members.

## 7. Contact Information & Grievance Redressal
In accordance with the Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021:
- **Grievance Officer:** Privacy & Security Desk
- **Email:** support@splitsaathi.example.com
- **Response Time:** Acknowledgment within 24 hours, resolution within 15 days.
