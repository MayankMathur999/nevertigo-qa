# NEVertigo schema for the query assistant

This is the first artifact for an intern building a semantic layer and an LLM that answers questions in SQL.

The full dump has about 590 tables. The assistant should see only the 23 tables in [`schema.sql`](schema.sql): the 16 tables behind the reports in [`../contract`](../contract), plus the 7 monthly clinic invoice tables.

Start with [`PROJECT.md`](PROJECT.md). That is the intern handbook: the business, the visit model, visit bills versus monthly invoices, and the build order. Then use [`GUIDE.md`](GUIDE.md) as the May 2026 lab. Load [`sample-data.sql`](sample-data.sql) after the schema, then run [`sample-checks.sql`](sample-checks.sql).

`schema.sql` keeps production column names and types. It drops passwords, tokens, images, and device-config columns that reports never use. If a column is not in this file, the model must not invent it.

## What the assistant is for

Natural-language questions about VertigoClinic operations, answered as MySQL `SELECT`s:

- patients, visits, new patients, follow-ups
- clinics, doctors, technicians
- diagnosis and BPPV
- medicines prescribed on a visit
- positional nystagmus (SPV) and DVA
- whether a report was received, and which software version is installed
- Test billing revenue and Analysis billing
- rehab protocol templates (not patient sessions)

## How to read the model

```
Clinic-Info
    ├── Doctor-Info          (Doctor-Info.ClinicID)
    ├── Patient-Info         (home clinic)
    ├── RegHW                (machines at the clinic)
    ├── NeuroInvoice         monthly test invoice (ClinicID, not a visit)
    ├── NeuroMonthInvoice    monthly rollup (clinicId)
    ├── NeuroExInvoice
    ├── NeuroRehabInvoice
    ├── NeuroManeuverInvoice
    ├── NeuroCounselingInvoice
    ├── NeuroExRehabInvoice
    └── Patient              (visit clinic)  ── Technician via Patient.TechID
            ├── Prescription
            ├── PositionalData
            ├── DVA
            ├── ReportRequest
            ├── AnalysisBill
            └── Bill

Patient-Info.DoctorID = Doctor-Info.Email
and Doctor-Info.ClinicID = Patient.ClinicID

AllMedicine / Medicine                 catalogs for Presjson medicine lines
RehabPosturographyPlans                protocol templates (no clinic, no patient)
ClinicRehabDiseaseExercise             protocol templates (no clinic, no patient)
```

A **visit** is one `(PatientID, ApDate)` row in `Patient`. Almost every clinical and billing table hangs off that pair.

A **patient** is one row in `Patient-Info`. `Patient-Info.ApDate` is the first visit. Later visits live only in `Patient`.

## Rules the semantic layer must encode

These are already decided in `contract/`. The model should not rediscover them.

| Question | Rule |
|----------|------|
| Which clinics? | `ClinicID LIKE 'VertigoClinic%'` on every clinic-scoped query, unless the user names clinics or asks for all clinics. |
| Which `ClinicID`? | `Patient.ClinicID` for visits, tests, prescriptions, and bills. `Patient-Info.ClinicID` for new-patient / registration counts. |
| Export clinics | `Clinic-Info.isExportClinic = 1`. Do not also apply `VertigoClinic%`. |
| New patients | `Patient-Info.ApDate` in the window. Join the first visit with `Patient.ApDate = Patient-Info.ApDate`. |
| Follow-up patient | Same `PatientID` has more than one `Patient` row, any date, any clinic. |
| Follow-up visit date | `Patient.ApDate` when it differs from `Patient-Info.ApDate`. Do not use `Patient-Info.RetDate`. |
| Clinic name | `CONCAT(Clinic-Info.Fname, ' ', Clinic-Info.Lname)`. |
| BPPV | `Prescription.Cimp` contains `bppv`, case-insensitive, after the exclusions in `contract/queries/bppv.md`. Not `Prescription.BPPV`. |
| Diagnosis | `Prescription.Cimp`, then `Patient.Impression`, then `Presjson.clinicRehabImpression`, then `Prescription.RehabImpression`. |
| Medicine line | `JSON_TABLE` on `Prescription.Presjson` path `$.medicine[*]`: `medname`, `saltname`, `dose`, `dosetime`. |
| Medicine company | `AllMedicine.BrandName` on `MedName = medname`, else `Medicine.BrandName` on `MedName = saltname`. |
| Positional SPV | `PositionalData` only. Compare `ABS()` of the text values. Do not use `PostPositionalData` or the `Torsion` table. |
| DVA report received | A `ReportRequest` row for that visit with `Test LIKE '%DVA%'`. |
| Installed DVA version / machine | `RegHW.DVA` and `RegHW.UID` on `Patient.ClinicID`. A clinic can have several machines. |
| Test revenue | `Bill.NCost` minus `Bill.NDiscount`, floored at 0 per bill. `Bill.NShare` is a percent, not rupees. Amounts are text; blank and `NA` are 0. |
| Who billed the test? | `Patient.TechID`, then `Technician`. `Bill` has no technician column. |
| Analysis amount | `AnalysisBill.Amount`, one row per visit. |
| CGHS vs cash | `Patient-Info.healthScheme = 'CGHS'` vs everything else, including NULL. |
| Rehab game plans | Template tables. No clinic filter. |

## Tables left out on purpose

| Left out | Why |
|----------|-----|
| `patient` (`NEVertigo_patient.sql`) | Different table. Visits are `Patient` from `NEVertigo_Patient0.sql`. |
| `PostPositionalData`, `Torsion` | Not the default positional source. |
| Passwords, salts, tokens, biometrics, logos | Not query metrics. |
| Game score tables, CRM logs, OTP tables | Not in the current report set. Add them only when a question family needs them. |

## Suggested next step

[`GUIDE.md`](GUIDE.md) is the intern briefing. The May 2026 sample is the first eval set. Build certified metrics that reproduce [`sample-checks.sql`](sample-checks.sql) before any model training.
