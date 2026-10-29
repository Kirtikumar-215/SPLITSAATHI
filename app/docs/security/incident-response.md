# SplitSaathi — Security Incident Response Plan

---

## 1. Scope & Objective
This document outlines the protocol for identifying, containing, mitigating, and reporting potential security vulnerabilities or data incidents affecting the SplitSaathi mobile application or backend infrastructure.

## 2. Severity Classification & Escalation Matrix

| Severity Level | Definition | Target Containment | Escalation Path |
|:---|:---|:---|:---|
| **P0 (Critical)** | Compromise of production credentials, database breach, unauthorized remote fund settlement spoofing. | Immediate (< 2 hours) | Lead Security Engineer, Database Admin |
| **P1 (High)** | Flaw in RLS allowing cross-group reads, CSV formula injection vulnerability. | < 12 hours | Backend & Android Maintainers |
| **P2 (Medium)** | Rate-limiting bypass on payment nudges, non-blocking logic discrepancies. | < 48 hours | Engineering Sprint Team |
| **P3 (Low)** | Minor UI contrast issue, non-sensitive edge case error messages. | Next standard release | Development Team |

## 3. Incident Response Steps
1. **Detection & Triage:** Validate the reported vulnerability or abnormal access pattern. Isolate affected services.
2. **Containment:**
   - In the event of credential leakage: Rotate Supabase database password, rotate API keys, invalidate compromised session tokens.
   - In the event of an RLS bypass: Apply emergency SQL patch via migration to restrict offending table policies.
3. **Eradication & Remediation:** Deploy validated hotfix with regression unit tests.
4. **Post-Mortem & Disclosure:** Prepare root-cause analysis (RCA) and notify affected stakeholders as required under relevant regulations.
