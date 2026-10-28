# NEVertigo query assistant — intern handbook

Read this first. It is the map of the project: what the assistant is for, how the database is shaped, which money tables answer which questions, and the order to build the work.

[`GUIDE.md`](GUIDE.md) is the lab that follows this handbook. It walks through a fictional May 2026 dataset and an answer key. [`../contract`](../contract) is the rule book. If this handbook and a contract file disagree, follow the contract.

## 1. What you are building

Clinics record vertigo visits in a MySQL 8 database named **NEVertigo**. People already ask questions against that database in SQL: how many new patients, how many had BPPV, what medicine was prescribed, what a clinic earned, whether a DVA report came back.

Your job is an assistant that answers those questions in SQL. Build it in three steps, in this order:

1. **Schema the model is allowed to see.** A small set of real tables, with real column names. The dump has about 590 tables. Showing all of them makes the model invent joins.
2. **Semantic layer.** One named metric per business concept, each with one certified SQL definition. "BPPV patient" and "net neuro revenue" must mean the same thing every time.
3. **Question to SQL.** A prompt, or later a fine-tune, checked against the May 2026 lab and then against the queries in `contract/queries/`. Train only after the metrics reproduce the answer key.

Success on the lab is concrete. After loading the sample, [`sample-checks.sql`](sample-checks.sql) returns the numbers in [`GUIDE.md`](GUIDE.md). Those numbers are the first exam.

## 2. Where the files live

| Path | Role |
|------|------|
| `NEVertigo_*.sql` in the dump root | Production table definitions from the 30 June 2026 dump. One file per table. Structure only. |
| [`../contract`](../contract) | Rules learned from real reports: clinic filter, BPPV, medicines, billing, DVA, rehab plans. |
| [`schema.sql`](schema.sql) | The 23 tables the assistant may use. Sixteen are visit, catalog, and protocol tables. Seven are monthly clinic invoices. Column names and types match production. Passwords, tokens, images, and PDF blobs are omitted. |
| [`sample-data.sql`](sample-data.sql) | Fictional clinics and patients. Safe to reload. |
| [`sample-checks.sql`](sample-checks.sql) | Certified SQL for the May 2026 window. |
| [`GUIDE.md`](GUIDE.md) | Why each sample row exists, and the answer key. |
| [`README.md`](README.md) | One-page table map. |

Load MySQL 8 like this:

```bash
mysql -u root -p < semantic-layer/schema.sql
mysql -u root -p < semantic-layer/sample-data.sql
mysql -u root -p NEVertigo < semantic-layer/sample-checks.sql
```

`schema.sql` creates the database once. `sample-data.sql` deletes and reinserts the sample, so you can rerun it.

The lowercase table `patient` in `NEVertigo_patient.sql` is a different table. Visits are `Patient`, defined in `NEVertigo_Patient0.sql`.

## 3. The business, in one pass

A **clinic** is a row in `Clinic-Info`. The clinics this project reports on use ids like `VertigoClinic001`. The default scope on every clinic question is:

```sql
AND ClinicID LIKE 'VertigoClinic%'
```

Put that predicate on `Patient.ClinicID` for visits, tests, prescriptions, and visit bills. Put it on `Patient-Info.ClinicID` only when the question is about registration or new patients. Apply it in every part of the query that reads those rows.

Two scopes replace that pattern when the user asks for them:

| User asks for | Predicate |
|---------------|-----------|
| Export clinics | `Clinic-Info.isExportClinic = 1` on the visit clinic. Leave off `VertigoClinic%`. Export ids often do not use that prefix. |
| Named clinics, or all clinics | Only what they named. |
| Active clinics only | Also `Clinic-Info.isActive = 1`. Inactive clinics stay in the default scope until someone asks for active only. |

The clinic's display name is `CONCAT(Fname, ' ', Lname)`.

A **patient** is one row in `Patient-Info`. `Patient-Info.ApDate` is the first visit, the registration date. `Patient-Info.ClinicID` is the home clinic. `Patient-Info.DoctorID` is the assigned doctor's email. `Patient-Info.healthScheme = 'CGHS'` means CGHS. Every other value, including NULL, is cash / general.

A **visit** is one row in `Patient`, keyed by `(PatientID, ApDate)`. `Patient.ClinicID` is the clinic where that visit happened. It can differ from the home clinic. `Patient.TechID` is the technician for that visit. A follow-up is another row for the same `PatientID`, on any date, at any clinic. `Patient-Info.RetDate` is almost never filled in. The follow-up date is the later `Patient.ApDate`.

A visit then has optional facts, each joined on `(PatientID, ApDate)`:

| Table | One row means |
|-------|----------------|
| `Prescription` | The clinical impression and the medicines for that visit |
| `PositionalData` | Positional nystagmus measurements for that visit |
| `DVA` | Dynamic visual acuity scores for that visit |
| `ReportRequest` | A report request. A visit can have more than one |
| `AnalysisBill` | The analysis charge for that visit |
| `Bill` | A test charge for that visit. A second row exists when `isRenBill` differs |

Doctors live in `Doctor-Info`. A doctor matches a visit only when both are true: `Patient-Info.DoctorID = Doctor-Info.Email` and `Doctor-Info.ClinicID = Patient.ClinicID`. A Mumbai doctor does not attach to a Pune visit.

Machines live in `RegHW`, one row per `(ClinicID, UID)`. Software versions on the machine, including `RegHW.DVA`, are properties of the clinic's hardware. They are not stored on the `DVA` test row.

`AllMedicine` and `Medicine` are catalogs. Rehab plan tables are protocol templates. They have no patient and no clinic.

## 4. Keep the three grains apart

Most wrong SQL in this project mixes these three grains.

### Person and visit

| Question | Grain | Table and date |
|----------|-------|----------------|
| How many people registered? | Patient | `Patient-Info.ApDate`, home `ClinicID` |
| How many visits, or who was seen? | Visit | `Patient.ApDate`, visit `ClinicID` |
| Is this person a follow-up? | Patient | More than one `Patient` row for that `PatientID`, any date |
| Is this date a follow-up visit? | Visit | `Patient.ApDate` differs from `Patient-Info.ApDate` |

A person can be new in May and a follow-up in May if their first visit and their second visit both fall in May. Count them in both metrics. The metrics answer different questions.

Visit counts use `COUNT(DISTINCT CONCAT(PatientID, '|', ApDate))`. Patient counts use `COUNT(DISTINCT PatientID)`.

### Visit money

This is what the current teaching schema and the May exam use.

`Bill` is the test charge for a visit. `AnalysisBill` is the analysis charge for a visit. Both hang off `(PatientID, ApDate)`. Summing them produces visit revenue for a date window.

`Bill.Invoice` is a text invoice number on that visit row. It is not a link to the monthly invoice tables below.

### Clinic-month invoices

These seven tables are in `schema.sql`. PDF blobs are omitted. The May lab does not load sample rows for them, so `sample-checks.sql` does not score invoice questions yet.

Each row is a document NeuroEquilibrium issues to a clinic for a calendar month and a financial year. The key is `(BillNumber, BillYear)`, with `ClinicID` and `Month` on the row. There is no `PatientID` and no visit date. `BillPdf` is the PDF blob. Leave the blob out of any assistant schema.

| Table | Document |
|-------|----------|
| `NeuroInvoice` | Monthly test invoice. Amounts in `BillAmount` and `NetAmount`. Payment in `PaymentDone`, `AmountReceived`, `PaymentDue`, `TDSAmount`. |
| `NeuroMonthInvoice` | One monthly rollup with separate text amounts for `test`, `rehab`, `counselling`, `maneuver`, and `totalAmount`. Column names on this table are camelCase (`clinicId`, `billNumber`), unlike the others. |
| `NeuroExInvoice` | Monthly invoice for an export clinic. |
| `NeuroRehabInvoice` | Monthly rehab invoice. |
| `NeuroManeuverInvoice` | Monthly maneuver invoice. |
| `NeuroCounselingInvoice` | Monthly counselling invoice. |
| `NeuroExRehabInvoice` | Monthly export rehab invoice. |

Use these tables for questions about the clinic's monthly bill, whether it was paid, or how test, rehab, counselling, and maneuver were split on that document.

Use `Bill` and `AnalysisBill` for questions about a patient, a visit, a technician, a day, or CGHS versus cash. Those facts are not on the monthly invoice.

There is no foreign key from a visit bill to `NeuroInvoice`. Do not join them on `Bill.Invoice = NeuroInvoice.BillNumber`. Those numbers are different sequences. A monthly invoice is the clinic-level document for a month. The visit rows are the underlying charges.

## 5. Other visit bills, and why they are not in the exam yet

The dump has more visit-level charge tables. The reports behind this project use `Bill` for tests and `AnalysisBill` for analysis. The CGHS report says rehab, counselling, and maneuver amounts were zero from April 2026 onward, so those reports stay on `Bill` until someone asks for another stream.

Know the others so you do not invent their columns on `Bill`:

| Table | Grain | Technician column |
|-------|-------|-------------------|
| `Bill` | `(PatientID, ApDate, isRenBill)` | None. Use `Patient.TechID`. |
| `AnalysisBill` | `(PatientID, ApDate)` | None. |
| `ClinicRehabBill` | `(PatientID, Invoice)`, date in `RhDate` | `billingTechnician` |
| `ClinicCounselingBill` | `(PatientID, Invoice)`, date in `CsDate` | `billingTechnician` |
| `ManeuverBill` | `(PatientID, ApDate, IsPostPositional)` | None on the table. |
| `RENBill` | Patient plus invoice and bill date | Separate from `Bill.isRenBill`. |

`Bill.isRenBill` already lets one visit have a normal test row and a REN row. That is the REN fact the current exam includes. `RENBill` is a different table.

Add one of these tables to the assistant schema only when a question family needs it, and add a contract note at the same time. Until then the model must not query them.

## 6. Tables in the teaching schema

```
Clinic-Info
    ├── Doctor-Info                 Doctor-Info.ClinicID
    ├── Patient-Info                home clinic
    ├── RegHW                       machines at the clinic
    └── Patient                     visit clinic
            ├── Technician          Patient.TechID
            ├── Prescription
            ├── PositionalData
            ├── DVA
            ├── ReportRequest
            ├── AnalysisBill
            └── Bill

AllMedicine, Medicine               catalogs for medicines inside Presjson
RehabPosturographyPlans             protocol template, no clinic, no patient
ClinicRehabDiseaseExercise          protocol template, no clinic, no patient
```

Standard from-clause for a visit question:

```sql
FROM `Patient-Info` pi
INNER JOIN `Patient` p
    ON p.`PatientID` = pi.`PatientID`
INNER JOIN `Prescription` pr
    ON pr.`PatientID` = p.`PatientID`
   AND pr.`ApDate` = p.`ApDate`
LEFT JOIN `Clinic-Info` ci
    ON ci.`ClinicID` = p.`ClinicID`
LEFT JOIN `Doctor-Info` di
    ON di.`Email` = pi.`DoctorID`
   AND di.`ClinicID` = p.`ClinicID`
WHERE p.`ClinicID` LIKE 'VertigoClinic%'
```

Drop `Prescription` when the question is not about diagnosis or medicine. Use `LEFT JOIN` for a test or a bill when a visit with no such row must still count.

`ReportRequest` can have several rows per visit (`isPostPositional`, `iteration`). Test "was a DVA report received?" with `EXISTS`, not an inner join. An inner join double-counts the visit.

## 7. Rules the semantic layer must encode

These are already decided. The model calls the metric. It does not re-derive the rule.

### Patients

- **New patient:** `Patient-Info.ApDate` falls in the window. Join the first visit with `Patient.ApDate = Patient-Info.ApDate` when you need tests from that first day.
- **Follow-up patient:** another `Patient` row exists for that `PatientID` on a different `ApDate`.
- **Follow-up visit date:** `Patient.ApDate` when it is not `Patient-Info.ApDate`.

### Diagnosis and BPPV

BPPV comes from `Prescription.Cimp`, case-insensitive `LIKE '%bppv%'`, after removing:

- `No BPPV`
- `Post-BPPV residual dizziness`
- `Post-traumatic BPPV`
- `The Positional tests do not indicate BPPV`

`Prescription.BPPV` is a separate column. Do not count BPPV from it.

For a percentage, use the same window and the same clinic predicate for the numerator and the denominator. Denominator is distinct patients with a visit in that window.

Diagnosis, first non-empty value:

1. `Prescription.Cimp`
2. `Patient.Impression`
3. `Presjson` field `clinicRehabImpression`
4. `Prescription.RehabImpression`

### Medicines

Medicines are JSON on `Prescription.Presjson`, path `$.medicine[*]`. Fields: `medname` (brand), `saltname` (salt), `dose`, `dosetime` (frequency). Read them with `JSON_TABLE`. Ignore rows whose `medname` is null or blank.

Company: `AllMedicine.BrandName` where `AllMedicine.MedName = medname`. If that brand is missing, `Medicine.BrandName` where `Medicine.MedName = saltname`.

One visit with three medicines is three detail rows. A consolidated report groups those lines and counts patients and visits. It does not print `PatientID`.

### Positional test and DVA

Positional slow-phase velocity is on `PositionalData`. Values are text and may be negative. Compare `ABS()`. The max for a visit is the largest absolute SPV across the SPV columns listed in [`../contract/queries/positional.md`](../contract/queries/positional.md). "No torsion" means every torsion column (`DHLT`, `DHRT`, `LLT`, `LRT`, `HHT`, `LeanT`, `BowT`) is null, blank, `False`, or `NA`.

`PostPositionalData` and the `Torsion` table are out of scope unless the user names them.

`DVA` stores scores only. A report was received when a `ReportRequest` for that visit has `Test LIKE '%DVA%'`. The installed version and the machine id are `RegHW.DVA` and `RegHW.UID` for `Patient.ClinicID`. A clinic can have several machines, so list them with `GROUP_CONCAT`.

### Visit revenue

Net neuro revenue of one `Bill` row:

```sql
GREATEST(n_cost - n_discount, 0)
```

`NCost` and `NDiscount` are text. Blank, `NA`, and `N/A` become 0. Floor each bill at zero, then sum. A discount larger than the neuro amount contributes zero, never a negative.

`NShare` is the neuro share **percent**. Summing it is not revenue.

Include every `isRenBill` value, and do not filter `Bill.Active`, unless the question says otherwise.

Date for test revenue is `Bill.ApDate`, which is the visit date. Clinic is `Patient.ClinicID`. Handler is `Clinic-Info.Handler`. Technician is `Patient.TechID` joined to `Technician`. Group technician revenue by handler and technician unless the user asks for handler totals only.

`AnalysisBill.Amount` is also text. One analysis bill per visit. Export analysis uses `isExportClinic = 1` and does not also filter `VertigoClinic%`.

CGHS versus cash uses `Patient-Info.healthScheme`, then the same net from `Bill`.

### Rehab templates

`RehabPosturographyPlans` and `ClinicRehabDiseaseExercise` store session plans as JSON inside blobs. Cast the blob to text, then to JSON. Top-level keys are session or day numbers. There is no clinic filter.

## 8. The May 2026 lab

The sample window is 1 May 2026 through 31 May 2026. The story is in [`GUIDE.md`](GUIDE.md). The shape of it:

- Three `VertigoClinic` sites, one inactive, still inside the default scope.
- One export clinic, outside that scope, inside `isExportClinic = 1`.
- One partner clinic that matches neither.
- Patients who are new, patients who are follow-ups, and one patient who is both.
- A home clinic that differs from the May visit clinic, so the doctor join fails.
- `Cimp` values that contain the letters bppv and still must be excluded.
- Bill rows with a percent in `NShare`, a non-numeric `NCost`, a discount above `NCost`, a REN row, and `Active = 0`.
- A DVA test whose report was not received, and a visit with two `ReportRequest` rows.
- A medicine brand missing from `AllMedicine`, so the company comes from the salt.

Run `sample-checks.sql`. These are the headline results for `VertigoClinic%` in May:

| Metric | Value |
|--------|-------|
| Visits | 8 |
| Patients seen | 7 |
| New patients | 5 |
| Follow-up patients | 3 |
| BPPV patients | 2 (28.57% of patients seen) |
| Net neuro test revenue | 11600 |
| Of which CGHS / cash | 1300 / 10300 |
| Analysis amount, Vertigo clinics | 4300 |
| Analysis amount, export clinics | 2200 |
| Patients with a received DVA report | 2 |
| Medicine lines | 4 |
| Visits with max SPV ≤ 6 and no torsion | 3 |

If your SQL disagrees, fix the SQL. Do not edit the sample to make a wrong query pass.

The lab has no monthly invoice rows. The seven invoice tables are in `schema.sql`, and invoice questions stay out of the exam until sample rows and check queries exist for them.

## 9. How to build the semantic layer

Give every metric a name, a grain, the filters it accepts, and one SQL body. The SQL body for the current exam is the matching query in `sample-checks.sql`.

Suggested first metrics:

| Name | Grain |
|------|-------|
| `visits` | `(PatientID, ApDate)` |
| `patients_with_visit` | patient |
| `new_patients` | patient, from `Patient-Info` |
| `followup_patients` | patient |
| `bppv_patients` | patient |
| `bppv_percentage` | the requested window |
| `diagnosis` | visit |
| `medicine_line` | one drug on one visit |
| `net_neuro_revenue` | one `Bill` row, then summed |
| `analysis_amount` | one `AnalysisBill` row |
| `dva_report_received` | visit |
| `max_abs_positional_spv` | visit |

Filters every metric should accept, with the defaults above: date window, clinic scope (`VertigoClinic%` or export or an explicit list), active-clinics-only off by default, REN rows included by default.

Dimensions to group by: clinic name, handler, technician, doctor type (`Doctor-Info.CType`), specialty, health scheme, month.

Write the metric down before you write a prompt. A prompt that pastes the raw `CREATE TABLE` and hopes is not a semantic layer.

When you later add monthly invoices, add separate metrics. For example `monthly_test_invoice_net` reads `NeuroInvoice.NetAmount` at clinic-month grain. It must not call `net_neuro_revenue`, and `net_neuro_revenue` must not read `NeuroInvoice`.

## 10. How the model should behave

The model receives the metric list, the join grain, and the clinic rule. It writes a `SELECT`. It does not get passwords, tokens, or the other 570 tables.

Check it on the questions in [`GUIDE.md`](GUIDE.md) first. A good answer matches the answer key. Then add question/SQL pairs from `contract/queries/`, using the same metric definitions.

Reject a generated query that:

- omits the clinic predicate on a clinic-scoped question
- uses `Patient-Info.ClinicID` for a visit or revenue question
- counts BPPV from `Prescription.BPPV`
- sums `Bill.NShare` as rupees
- treats `Bill.Invoice` as `NeuroInvoice.BillNumber`
- joins `ReportRequest` in a way that duplicates a visit
- reads `PostPositionalData` for an ordinary positional question
- invents a column that is not in `schema.sql`

## 11. Work order

1. Load the schema and the sample. Run the checks. Read [`GUIDE.md`](GUIDE.md) until you can explain each headline number.
2. Implement the metrics in section 9 so each one returns the check value.
3. Write the 14 eval questions in the lab guide as natural language paired with that SQL.
4. Only then put a model in front of the metrics and score it on those questions.
5. Add a new table family, such as the monthly invoices or `ClinicRehabBill`, only with a contract note, sample rows, and a check query.

## 12. When you learn a new rule

Update the matching file in `contract/` and `contract/defaults.json`. Then update this handbook if the rule changes what the intern should know, and update `schema.sql` if the assistant is now allowed to see another table. A rule that lives only in a chat will be lost.

## 13. Glossary

| Term | Meaning here |
|------|----------------|
| Visit | One `(PatientID, ApDate)` in `Patient`. |
| New patient | `Patient-Info.ApDate` in the reporting window. |
| Follow-up patient | That patient has more than one visit row, any date. |
| Home clinic | `Patient-Info.ClinicID`. |
| Visit clinic | `Patient.ClinicID`. |
| BPPV | A qualifying `Prescription.Cimp`, after the four exclusions. |
| `NShare` | Neuro share percent on a visit bill. |
| `NCost` | Neuro share amount in rupees on a visit bill. |
| Net neuro revenue | `NCost - NDiscount`, floored at 0 per bill. |
| `Bill.Invoice` | Invoice number text on a visit charge. |
| `NeuroInvoice` | Monthly test invoice issued to a clinic. |
| CGHS | `Patient-Info.healthScheme = 'CGHS'`. |
| Export clinic | `Clinic-Info.isExportClinic = 1`. |
| DVA report received | A `ReportRequest` for that visit whose `Test` contains `DVA`. |
| Semantic layer | Named metrics with fixed SQL, which the model is supposed to call. |
