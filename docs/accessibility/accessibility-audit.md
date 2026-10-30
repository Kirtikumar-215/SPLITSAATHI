# SplitSaathi — Accessibility & Usability Audit (WCAG 2.2 Standards)

---

## 1. Compliance Scope
SplitSaathi was audited against **WCAG 2.2 Level AA** criteria on Android (TalkBack and Touch Navigation).

## 2. Accessibility Checklist & Findings

| Category | Requirement | Audit Result | Status |
|:---|:---|:---|:---|
| **Touch Targets** | Minimum interactive touch target of 48dp x 48dp (Material 3 standard). | Primary buttons (`Button`, `OutlinedButton`, FABs, navigation items) meet or exceed 48dp height/width. | **VERIFIED PASS** |
| **Color Contrast** | Minimum 4.5:1 contrast ratio for normal text; 3:1 for large text. | All M3 dynamic and themed text elements use `onSurface`, `onPrimary`, or `onSurfaceVariant` over compatible container backgrounds. | **VERIFIED PASS** |
| **Screen Readers (TalkBack)** | All interactive icons and image views have clear, meaningful `contentDescription` or are marked decorative (`null`). | Navigation icons have localized resource descriptions (`nav_groups`, `nav_activity`, `nav_reports`, `nav_profile`); decorative icons marked `contentDescription = null`. Directional icons use `AutoMirrored` icons. | **VERIFIED PASS** |
| **Automated Test Identifiers** | Primary interactive elements have distinct `Modifier.testTag(...)`. | Key flows tagged: `profile_display_name_input`, `profile_phone_input`, `profile_upi_input`, `profile_save_btn`, `profile_delete_account_btn`, `export_csv_btn`, `main_bottom_nav`. | **VERIFIED PASS** |
| **Dynamic Type & Scalability** | Text elements utilize scalable units (`sp`) and layouts use adaptive flex units (`dp`). | No hardcoded fixed-pixel dimensions that break on system font scaling. | **VERIFIED PASS** |
