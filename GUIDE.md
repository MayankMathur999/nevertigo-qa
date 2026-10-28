# Lab guide for the query-assistant intern

[`PROJECT.md`](PROJECT.md) explains the project. This file is the May 2026 lab: the sample rows, the traps, and the answer key for the 16-table schema.

The sample is fictional. It is small on purpose. Every patient exists to make one wrong shortcut produce a different number from the answer key.

## Files

| File | What it is |
|------|------------|
| [`PROJECT.md`](PROJECT.md) | Project handbook. Read it before this lab. |
| [`schema.sql`](schema.sql) | Tables, keys, and column comments. Load this first. |
| [`sample-data.sql`](sample-data.sql) | Fictional clinics, patients, visits, tests, and bills. Safe to reload. |
| [`sample-checks.sql`](sample-checks.sql) | The May 2026 answer key as queries. |
| [`README.md`](README.md) | Short map of the tables and the rules. |
| [`../contract`](../contract) | The production rules. When this guide and a contract disagree, follow the contract. |

## Load

MySQL 8.0 or newer:

```bash
mysql -u root -p < semantic-layer/schema.sql
mysql -u root -p < semantic-layer/sample-data.sql
mysql -u root -p NEVertigo < semantic-layer/sample-checks.sql
```

`sample-data.sql` deletes the sample rows and inserts them again, so you can rerun it. `schema.sql` creates the tables once.

Every check uses this window:

```sql
SET @start_date = '2026-05-01';
SET @end_date   = '2026-05-31';
```

If a check does not match the answer key below, the SQL is wrong. Fix the SQL before changing the sample.

## What you are building

Three layers, in this order.

1. **Schema.** Already done. The model may see the tables in `schema.sql` and no others. The May lab exercises the visit tables. The seven monthly invoice tables are in the schema and have no sample rows yet.
2. **Semantic layer.** Named metrics with one certified SQL definition each: visit, new patient, follow-up patient, BPPV patient, net neuro revenue, DVA report received, and the rest of the answer key. The model should call these metrics. It should not re-derive BPPV or revenue from raw columns in a new way each time.
3. **Question to SQL.** A prompt or a fine-tune whose eval set is the questions in this guide. A change is good only when `sample-checks.sql` still returns the answer key.

Do the semantic layer before any training. A model that can see all 590 dump tables will invent joins.

## The sample world

Five clinics:

| ClinicID | Name | In default scope? | Why it is here |
|----------|------|-------------------|----------------|
| `VertigoClinic001` | Mumbai Central Clinic | Yes | Handler Anita Rao. Two machines. |
| `VertigoClinic002` | Pune Camp Clinic | Yes | Same handler, different technician. |
| `VertigoClinic003` | Delhi South Clinic | Yes | `isActive = 0`. Still included unless someone asks for active clinics only. |
| `ExportHyderabad` | Hyderabad Export Clinic | No | `isExportClinic = 1`. Use this flag instead of `VertigoClinic%`. |
| `PartnerGoa` | Goa Partner Clinic | No | Neither filter matches. No patients. |

Default clinic predicate, on `Patient.ClinicID` for visits and bills:

```sql
AND p.`ClinicID` LIKE 'VertigoClinic%'
```

Clinic display name is `CONCAT(Fname, ' ', Lname)`, so Mumbai is `Mumbai Central Clinic`.

Technicians: Riya Shah (`TechID` 101), Arjun Nair (102), Meera Das (103). `Bill` has no technician column. The technician is `Patient.TechID` on that visit.

Doctors live on `Doctor-Info` and match only when **both** are true: `Patient-Info.DoctorID = Doctor-Info.Email` and `Doctor-Info.ClinicID = Patient.ClinicID`.

## Patients

`Patient-Info` is the person. `Patient-Info.ApDate` is the first visit. `Patient` is one row per visit. A later visit does not change the first-visit date.

| ID | Name | First visit | Home clinic | Scheme | Why this row exists |
|----|------|-------------|-------------|--------|---------------------|
| P1001 | Asha Patil | 2026-05-04 | Mumbai | Cash (`NULL`) | New patient. True BPPV. Two medicines. DVA report received. SPV 12 with torsion. |
| P1002 | Ravi Deshmukh | 2026-04-10 | Mumbai | CGHS | Not new in May. Follow-up. `Cimp` is `No BPPV` while `Prescription.BPPV` is `Yes`. Discount larger than neuro amount. DVA exists, report does not. `RetDate` is a decoy. |
| P1003 | Neha Kulkarni | 2026-05-18 | Pune | CGHS | New, and a follow-up because she has two visits. 18 May is BPPV. 25 May is `Post-BPPV residual dizziness`. One bill is `NA`, one is a REN bill. |
| P1004 | Kabir Singh | 2026-05-20 | Delhi South | Cash | New patient at an inactive clinic. He still counts. |
| P1005 | Lata Reddy | 2026-05-08 | Hyderabad Export | Cash | True BPPV text, but the clinic is export-only. She is outside every default total. |
| P1006 | Omar Shaikh | 2026-03-01 | Mumbai | Cash | May visit is in Pune, so the visit clinic and the home clinic differ. Diagnosis comes from `Presjson`. Brand `VERTIN` is missing from `AllMedicine`. His doctor does not match the Pune visit. |
| P1007 | Isha Gupta | 2026-05-11 | Mumbai | Cash | `Post-traumatic BPPV` is excluded. `Prescription.BPPV` is `Yes`. Low SPV, no torsion. |
| P1008 | Farhan Ali | 2026-05-28 | Pune | Cash | `The Positional tests do not indicate BPPV` is excluded. His bill has `Active = 0` and still counts. |

May visits inside `VertigoClinic%`:

| Patient | Visit date | Clinic | Technician | Counts as BPPV? |
|---------|------------|--------|------------|-----------------|
| P1001 | 2026-05-04 | Mumbai | Riya Shah | Yes |
| P1002 | 2026-05-12 | Mumbai | Riya Shah | No (`No BPPV`) |
| P1003 | 2026-05-18 | Pune | Arjun Nair | Yes |
| P1003 | 2026-05-25 | Pune | Arjun Nair | No (residual dizziness) |
| P1004 | 2026-05-20 | Delhi South | Meera Das | No |
| P1006 | 2026-05-22 | Pune | Arjun Nair | No |
| P1007 | 2026-05-11 | Mumbai | Riya Shah | No (post-traumatic) |
| P1008 | 2026-05-28 | Pune | Arjun Nair | No |

That is **8 visits** and **7 patients**. P1005 is not in this list. P1002's April visit and P1006's March visit are real rows and must stay out of the May window.

## Rules, with the sample as proof

### New patient vs visit vs follow-up

- **New patients in May:** `Patient-Info.ApDate` in May and `Patient-Info.ClinicID LIKE 'VertigoClinic%'`. Result: P1001, P1003, P1004, P1007, P1008. Count **5**.
- **Patients with a visit in May:** distinct `PatientID` from `Patient` in the window. Result: the 7 people in the visit table. P1002 and P1006 are in this count and are not new patients.
- **Follow-up patient:** that `PatientID` has another `Patient` row on a different date, anywhere. P1002, P1003, and P1006. Count **3**. P1003 is both a new patient and a follow-up patient.
- **Follow-up visit date:** `Patient.ApDate` when it is not `Patient-Info.ApDate`. For P1002 that date is `2026-05-12`. His `RetDate` of `2026-01-15` is not the follow-up date.

P1006 shows why the clinic column matters. His home clinic is Mumbai. His May visit clinic is Pune. A visit report uses Pune. A new-patient report would use Mumbai, and he is not new in May anyway.

### BPPV

Count a visit only when `Prescription.Cimp` contains `bppv`, then drop these values:

- `No BPPV` (P1002)
- `Post-BPPV residual dizziness` (P1003 on 25 May)
- `Post-traumatic BPPV` (P1007)
- `The Positional tests do not indicate BPPV` (P1008)

`Prescription.BPPV = 'Yes'` is the wrong test. It would also count P1002 and P1007.

Distinct BPPV patients in the May visit window: **P1001 and P1003**. Denominator: **7** patients with a visit. Percentage: **28.57**.

P1005's impression is real BPPV, and she stays out because her clinic fails `VertigoClinic%`.

### Diagnosis and medicines

Diagnosis, first non-empty value:

1. `Prescription.Cimp`
2. `Patient.Impression`
3. `Presjson` field `clinicRehabImpression`
4. `Prescription.RehabImpression`

P1001's diagnosis is `Left posterior canal BPPV` from `Cimp`, even though `Patient.Impression` says `Dizziness`.

P1006's May `Cimp` is empty and `Impression` is empty, so the diagnosis is `Labyrinthine Concussion`, not `Unspecified dizziness`.

Medicines are lines inside `Prescription.Presjson`, path `$.medicine[*]`, fields `medname`, `saltname`, `dose`, `dosetime`. May lines inside the default clinic scope: **4**.

| Visit | Brand (`medname`) | Salt | Company | Why that company |
|-------|-------------------|------|---------|------------------|
| P1001 4 May | MECOVON-OD | METHYLCOBALAMIN | Micro Labs | `AllMedicine` matches the brand. Ignore `Medicine` row Generic Labs. |
| P1001 4 May | STEMETIL | CINNARIZINE | Abbott | Brand match. Ignore Wallace. |
| P1003 18 May | MECOVON-OD | METHYLCOBALAMIN | Micro Labs | Brand match. |
| P1006 22 May | VERTIN | BETAHISTINE | Abbott India | `VERTIN` is not in `AllMedicine`, so use `Medicine` on the salt. |

P1006's May doctor is **none**. `anil.mehta@clinic.test` belongs to Mumbai, and the visit is in Pune, so the doctor join does not match.

### Test revenue (`Bill`)

Net neuro revenue for one bill:

```sql
GREATEST(n_cost - n_discount, 0)
```

`NCost` and `NDiscount` are text. Blank, `NA`, and `N/A` are 0. Apply the floor on each bill, then sum.

`NShare` is a percent. Summing it is not revenue.

Do not filter `Bill.Active`. Include both `isRenBill = 'No'` and `isRenBill = 'Yes'` unless the question says to drop REN bills.

May bills inside `VertigoClinic%`:

| Bill | NShare % | NCost | NDiscount | Net | Why |
|------|----------|-------|-----------|-----|-----|
| P1001 4 May | 30 | 3000 | 500 | 2500 | Ordinary bill. |
| P1002 12 May | 40 | 2000 | 2500 | 0 | Discount wins, so the bill contributes zero, not −500. |
| P1003 18 May | 25 | NA | blank | 0 | Non-numeric amounts are zero. |
| P1003 25 May | 30 | 1000 | 0 | 1000 | Ordinary bill. |
| P1003 25 May REN | 30 | 400 | 100 | 300 | Second bill on the same visit. The primary key includes `isRenBill`. |
| P1004 20 May | 20 | 5000 | 0 | 5000 | Inactive clinic, still in scope. |
| P1006 22 May | 30 | 1500 | 200 | 1300 | Attributed to Pune and Arjun Nair, not to Omar's home clinic. |
| P1007 11 May | 30 | 700 | 0 | 700 | Ordinary bill. |
| P1008 28 May | 30 | 900 | 100 | 800 | `Active = 0`, still included. |

**Total net neuro revenue: 11600.**

CGHS is `Patient-Info.healthScheme = 'CGHS'` (P1002 and P1003 only): **1300**. Cash is every other scheme, including NULL: **10300**.

Handler and technician use the visit, then `Clinic-Info.Handler` and `Technician`:

| Group | Net |
|-------|-----|
| Anita Rao | 6600 |
| Rahul Mehta | 5000 |
| Riya Shah | 3200 |
| Arjun Nair | 3400 |
| Meera Das | 5000 |

P1002 also has an April bill of 1000. It is outside May. P1005's bill of 1800 is outside `VertigoClinic%`.

### Analysis billing

`AnalysisBill.Amount` is text, one row per visit. May total for `VertigoClinic%`: 1500 + 900 + 800 + 600 + 500 = **4300**. P1002's April analysis of 400 is outside the window.

Export clinics are a different question:

```sql
AND ci.`isExportClinic` = 1
```

May export Analysis total: **2200** (P1005 only). Adding `VertigoClinic%` to that query drops her and returns 0.

### DVA, reports, and installed software

`DVA` stores scores for a visit. It does not store the clinic, the machine, or the software version.

A report was received when some `ReportRequest` row for that `PatientID` + `ApDate` has `Test LIKE '%DVA%'`. Use `EXISTS`. P1001 has two request rows (`VNG` and `DVA`); an inner join counts her visit twice.

| Visit | DVA row | Report received |
|-------|---------|-----------------|
| P1001 4 May | Yes | Yes |
| P1002 12 May | Yes | No (`Test` is `VNG` only) |
| P1003 18 May | Yes | Yes |

Distinct patients with a received DVA report in May: **2**.

Installed version and machine come from `RegHW` on the visit clinic. Mumbai has two machines: `MACHINE-001A=2.4, MACHINE-001B=2.1`.

### Positional SPV

Use `PositionalData`. Compare `ABS()` of the text values. A "no torsion" visit has every torsion column NULL, blank, `False`, or `NA`.

May visits with max absolute SPV `<= 6` and no torsion: P1002 on 12 May (max 3), P1003 on 25 May (max 2), P1007 on 11 May (max 5). Count **3**. P1001 (max 12, torsion `True`) and P1003 on 18 May (max 8) stay out.

### Rehab plans

`RehabPosturographyPlans` and `ClinicRehabDiseaseExercise` are protocol templates. They have no `ClinicID` and no `PatientID`. The clinic filter does not apply. The game lists are JSON stored in a blob; cast the blob to text, then to JSON. Session `1` of the under-60 BPPV posturography plan enables `Hit The Nail` and disables `Stack`.

## Answer key

`sample-checks.sql` returns these rows.

| metric_name | metric_value |
|-------------|--------------|
| may_analysis_export_clinics | 2200 |
| may_analysis_vertigo_clinics | 4300 |
| may_bppv_patients | 2 |
| may_bppv_percentage | 28.57 |
| may_cash_net_neuro_revenue | 10300 |
| may_cghs_net_neuro_revenue | 1300 |
| may_dva_report_received_patients | 2 |
| may_followup_patients | 3 |
| may_low_spv_no_torsion_visits | 3 |
| may_medicine_lines | 4 |
| may_net_neuro_revenue | 11600 |
| may_new_patients | 5 |
| may_patients | 7 |
| may_revenue_anita_rao | 6600 |
| may_revenue_arjun_nair | 3400 |
| may_revenue_meera_das | 5000 |
| may_revenue_rahul_mehta | 5000 |
| may_revenue_riya_shah | 3200 |
| may_visits | 8 |
| mumbai_dva_versions | MACHINE-001A=2.4, MACHINE-001B=2.1 |
| p1001_mecovon_company | Micro Labs |
| p1006_may_diagnosis | Labyrinthine Concussion |
| p1006_may_doctor | NONE |
| p1006_vertin_company | Abbott India |

## Semantic layer to implement

Give each of these one name, one grain, and one SQL body. The SQL body is the matching query in `sample-checks.sql`.

| Metric | Grain | Sample value |
|--------|-------|--------------|
| `visits` | visit `(PatientID, ApDate)` | 8 |
| `patients_with_visit` | patient | 7 |
| `new_patients` | patient, from `Patient-Info` | 5 |
| `followup_patients` | patient | 3 |
| `bppv_patients` | patient | 2 |
| `bppv_percentage` | window | 28.57 |
| `net_neuro_revenue` | bill, then sum | 11600 |
| `analysis_amount` | visit bill | 4300 for Vertigo clinics, 2200 for export |
| `dva_report_received` | visit | 2 patients |
| `medicine_line` | one prescribed drug on a visit | 4 |
| `max_abs_positional_spv` | visit | used by the low-SPV count of 3 |

Dimensions the metrics group by: clinic name, handler, technician, doctor type, doctor specialty, health scheme, visit month.

Filters the semantic layer must expose:

- date window on the visit date (`Patient.ApDate` or `Bill.ApDate`, which are the same day)
- default clinic scope `VertigoClinic%`
- export scope `isExportClinic = 1`, which replaces the default scope
- optional "active clinics only" (`isActive = 1`), off unless the question asks for it
- optional "exclude REN bills", off unless the question asks for it

## Questions to use as the eval set

Write the SQL for each question, run it on the sample, and keep the pair only if the number matches.

1. How many visits did Vertigo clinics have in May 2026? **8**
2. How many distinct patients were seen? **7**
3. How many new patients registered in May? **5**
4. How many of the patients seen in May are follow-ups? **3**
5. How many BPPV patients, and what percent of patients seen? **2, 28.57%**
6. What was net neuro Test revenue? **11600**
7. Split that revenue into CGHS and cash. **1300 and 10300**
8. Split that revenue by handler and by technician. **Anita Rao 6600, Rahul Mehta 5000; Riya 3200, Arjun 3400, Meera 5000**
9. What was Analysis billing for Vertigo clinics, and for export clinics? **4300 and 2200**
10. How many patients had a DVA test whose report was received? **2**
11. Which DVA versions are installed in Mumbai? **2.4 and 2.1**
12. What was Omar Shaikh's diagnosis on 22 May, and which doctor is attached to that visit? **Labyrinthine Concussion, no doctor**
13. Which company should be reported for MECOVON-OD and for VERTIN? **Micro Labs and Abbott India**
14. How many May visits had max positional SPV at or below 6 and no torsion? **3**

After these pass, add question/SQL pairs from [`../contract/queries`](../contract/queries) using the same metric definitions. That set is what the model should be trained or prompted against.
