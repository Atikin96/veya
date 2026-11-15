# VEYA logical database diagram

Logical model of schema `veya` (PostgreSQL 17, Flyway V1–V10). It refines the BA data model
(Fig. B.11) with the entities required by the Must stories and the agreed decisions Q2–Q7
(see [`../ba-open-questions.md`](../ba-open-questions.md)). Every attribute is described in the
[data dictionary](data-dictionary.md). The diagrams render on GitHub (Mermaid).

Notation: `||--o{` one-to-many, `||--o|` one-to-zero-or-one, `}o--||` many-to-one;
PK primary key, FK foreign key, UK unique. Relationship types used: one-to-one (`app_user`–`user_profile`),
one-to-zero-or-one (`recovery_proposal`–`recovery_decision`), one-to-many (most), many-to-many through
junction tables (`meal_component`, `food_item_ingredient`, `exercise_equipment`, `profile_food_exclusion`,
`daily_constraint_ingredient`, `daily_constraint_equipment`).

## 0. Main blocks (colour-coded overview)

Each box is a table; arrows point from the referencing table to the referenced one (foreign key).

```mermaid
flowchart LR
    subgraph REF["Reference data"]
        goal_type; meal_slot; equipment; ingredient; consent_document; recovery_setting
    end
    subgraph CAT["Catalogue (local candidates, US-05/09/12)"]
        food_item; food_item_ingredient; meal; meal_component; exercise; exercise_equipment
    end
    subgraph IDN["Identity, consent, profile (US-01..04, LEG-2)"]
        app_user; user_consent; user_profile; profile_food_exclusion
    end
    subgraph CON["Daily constraints (US-03)"]
        daily_constraint; daily_constraint_ingredient; daily_constraint_equipment
    end
    subgraph PLN["Versioned plans (Q2, US-09/10/14)"]
        daily_plan; plan_version; plan_item
    end
    subgraph ACT["Confirmed actual records (US-06/08/13)"]
        food_entry; activity_entry; saved_meal; saved_meal_item
    end
    subgraph REC["Dynamic Plan Recovery (US-15..19)"]
        deviation_event; recovery_proposal; recovery_change; recovery_decision
    end

    food_item_ingredient --> food_item & ingredient
    meal_component --> meal & food_item
    meal --> meal_slot
    exercise_equipment --> exercise & equipment
    user_consent --> app_user & consent_document
    user_profile --> app_user & goal_type
    profile_food_exclusion --> user_profile & ingredient
    daily_constraint --> app_user
    daily_constraint_ingredient --> daily_constraint & ingredient
    daily_constraint_equipment --> daily_constraint & equipment
    daily_plan --> app_user
    plan_version --> daily_plan
    plan_item --> plan_version & meal & exercise & meal_slot
    food_entry --> app_user & plan_item & food_item & saved_meal & meal_slot
    activity_entry --> app_user & plan_item & exercise
    saved_meal --> app_user
    saved_meal_item --> saved_meal & food_item
    deviation_event --> daily_plan & plan_item
    recovery_proposal --> daily_plan
    recovery_change --> recovery_proposal & plan_item & meal & exercise
    recovery_decision --> recovery_proposal & plan_version

    classDef ref fill:#eceff1,stroke:#546e7a,color:#263238
    classDef cat fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef idn fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef con fill:#fff8e1,stroke:#f9a825,color:#5d4037
    classDef pln fill:#ede7f6,stroke:#5e35b1,color:#311b92
    classDef act fill:#fce4ec,stroke:#c2185b,color:#880e4f
    classDef rec fill:#ffebee,stroke:#c62828,color:#b71c1c
    class goal_type,meal_slot,equipment,ingredient,consent_document,recovery_setting ref
    class food_item,food_item_ingredient,meal,meal_component,exercise,exercise_equipment cat
    class app_user,user_consent,user_profile,profile_food_exclusion idn
    class daily_constraint,daily_constraint_ingredient,daily_constraint_equipment con
    class daily_plan,plan_version,plan_item pln
    class food_entry,activity_entry,saved_meal,saved_meal_item act
    class deviation_event,recovery_proposal,recovery_change,recovery_decision rec
```

## 1. Users, plans, actual records and recovery

```mermaid
erDiagram
    APP_USER ||--o| USER_PROFILE : "has (US-02)"
    APP_USER ||--o{ USER_CONSENT : "gives (LEG-2)"
    USER_PROFILE ||--o{ PROFILE_FOOD_EXCLUSION : "permanent exclusions (Q5)"
    APP_USER ||--o{ DAILY_CONSTRAINT : "per date (US-03)"
    DAILY_CONSTRAINT ||--o{ DAILY_CONSTRAINT_INGREDIENT : "available / excluded today"
    DAILY_CONSTRAINT ||--o{ DAILY_CONSTRAINT_EQUIPMENT : "available today"
    APP_USER ||--o{ DAILY_PLAN : "one per date"
    DAILY_PLAN ||--|{ PLAN_VERSION : "immutable versions (Q2)"
    PLAN_VERSION ||--o{ PLAN_ITEM : contains
    APP_USER ||--o{ FOOD_ENTRY : "diary (US-06)"
    APP_USER ||--o{ ACTIVITY_ENTRY : "workouts (US-13)"
    APP_USER ||--o{ SAVED_MEAL : "templates (US-08)"
    SAVED_MEAL ||--|{ SAVED_MEAL_ITEM : lines
    PLAN_ITEM |o--o{ FOOD_ENTRY : "fulfilled by"
    PLAN_ITEM |o--o| ACTIVITY_ENTRY : "outcome"
    SAVED_MEAL |o--o{ FOOD_ENTRY : "re-logged from"
    DAILY_PLAN ||--o{ DEVIATION_EVENT : "sets recoveryRequired (US-15)"
    PLAN_ITEM |o--o{ DEVIATION_EVENT : "deviating item"
    DAILY_PLAN ||--o{ RECOVERY_PROPOSAL : "US-16"
    RECOVERY_PROPOSAL ||--|{ RECOVERY_CHANGE : "reason per change (US-17)"
    PLAN_ITEM |o--o{ RECOVERY_CHANGE : "replaced / removed item"
    RECOVERY_PROPOSAL ||--o| RECOVERY_DECISION : "ACCEPT / EDIT / REJECT (US-18)"
    RECOVERY_DECISION |o--o| PLAN_VERSION : "creates (ACCEPT/EDIT)"

    APP_USER {
        uuid id PK
        varchar email UK "unique lower(email)"
        varchar password_hash "adaptive hash only"
        timestamptz adult_confirmed_at "Q8"
        timestamptz created_at
    }
    USER_PROFILE {
        uuid user_id PK,FK
        varchar goal_code FK
        int energy_target_kcal "stored goal, not calculated"
        numeric protein_target_g
        numeric fat_target_g
        numeric carbs_target_g
    }
    USER_CONSENT {
        uuid id PK
        uuid user_id FK
        smallint consent_document_id FK
        timestamptz granted_at
        timestamptz withdrawn_at "set once"
    }
    PROFILE_FOOD_EXCLUSION {
        uuid user_id PK,FK
        varchar ingredient_code PK,FK
    }
    DAILY_CONSTRAINT {
        uuid id PK
        uuid user_id FK
        date constraint_date "UK with user_id"
        smallint available_time_min
    }
    DAILY_CONSTRAINT_INGREDIENT {
        uuid daily_constraint_id PK,FK
        varchar ingredient_code PK,FK
        varchar availability "AVAILABLE or EXCLUDED"
    }
    DAILY_CONSTRAINT_EQUIPMENT {
        uuid daily_constraint_id PK,FK
        varchar equipment_code PK,FK
    }
    DAILY_PLAN {
        uuid id PK
        uuid user_id FK
        date plan_date "UK with user_id"
        int current_version_no "active version / optimistic lock"
        boolean recovery_required
    }
    PLAN_VERSION {
        uuid id PK
        uuid daily_plan_id FK
        int version_no "UK with plan, gap-free"
        varchar change_reason
    }
    PLAN_ITEM {
        uuid id PK
        uuid plan_version_id FK
        uuid item_key "logical item across versions"
        varchar item_type "MEAL or WORKOUT"
        bigint meal_id FK
        bigint exercise_id FK
        numeric planned_kcal "snapshot"
        smallint duration_min
        varchar status "PLANNED to COMPLETED/SKIPPED/MISSED/CHANGED"
    }
    FOOD_ENTRY {
        uuid id PK
        uuid user_id FK
        date entry_date
        varchar input_source "CATALOG or MANUAL or SAVED_MEAL"
        uuid plan_item_id FK
        numeric kcal "confirmed value"
    }
    ACTIVITY_ENTRY {
        uuid id PK
        uuid user_id FK
        bigint exercise_id FK
        uuid plan_item_id FK,UK
        varchar status "COMPLETED or SKIPPED or CHANGED"
        smallint duration_min
    }
    SAVED_MEAL {
        uuid id PK
        uuid user_id FK
        varchar name "UK with user_id"
    }
    SAVED_MEAL_ITEM {
        uuid saved_meal_id PK,FK
        smallint line_no PK
        numeric kcal
    }
    DEVIATION_EVENT {
        uuid id PK
        uuid daily_plan_id FK
        uuid plan_item_id FK
        varchar deviation_type
        numeric kcal_difference
        timestamptz resolved_at
    }
    RECOVERY_PROPOSAL {
        uuid id PK
        uuid daily_plan_id FK
        int base_version_no "baseVersion"
        timestamptz created_at
    }
    RECOVERY_CHANGE {
        uuid id PK
        uuid proposal_id FK
        varchar change_type "REPLACE or REMOVE or ADD"
        uuid target_item_id FK "old value"
        varchar reason_category
        varchar reason_text
    }
    RECOVERY_DECISION {
        uuid id PK
        uuid proposal_id FK,UK
        varchar decision
        uuid resulting_version_id FK,UK
        timestamptz decided_at
    }
```

## 2. Catalogue and reference data

```mermaid
erDiagram
    MEAL ||--|{ MEAL_COMPONENT : "made of"
    FOOD_ITEM ||--o{ MEAL_COMPONENT : "used in"
    FOOD_ITEM ||--o{ FOOD_ITEM_INGREDIENT : contains
    INGREDIENT ||--o{ FOOD_ITEM_INGREDIENT : "found in"
    EXERCISE ||--o{ EXERCISE_EQUIPMENT : requires
    EQUIPMENT ||--o{ EXERCISE_EQUIPMENT : "required by"
    MEAL_SLOT ||--o{ MEAL : "preferred slot"
    GOAL_TYPE ||--o{ USER_PROFILE : selected
    INGREDIENT ||--o{ PROFILE_FOOD_EXCLUSION : excluded
    CONSENT_DOCUMENT ||--o{ USER_CONSENT : accepted
    MEAL ||--o{ PLAN_ITEM : planned
    EXERCISE ||--o{ PLAN_ITEM : planned

    FOOD_ITEM {
        bigint id PK
        varchar name "UK lower(name) for LOCAL"
        varchar source "LOCAL or PROVIDER"
        varchar external_ref "UK with source"
        numeric kcal_per_100g
        numeric protein_per_100g
        numeric fat_per_100g
        numeric carbs_per_100g
    }
    MEAL {
        bigint id PK
        varchar name UK
        varchar meal_slot_code FK
        smallint prep_time_min
    }
    MEAL_COMPONENT {
        bigint meal_id PK,FK
        bigint food_item_id PK,FK
        numeric grams
    }
    INGREDIENT {
        varchar code PK
        varchar name
    }
    FOOD_ITEM_INGREDIENT {
        bigint food_item_id PK,FK
        varchar ingredient_code PK,FK
    }
    EXERCISE {
        bigint id PK
        varchar name
        numeric met_value
        varchar source
    }
    EXERCISE_EQUIPMENT {
        bigint exercise_id PK,FK
        varchar equipment_code PK,FK
    }
    EQUIPMENT {
        varchar code PK
        varchar name
    }
    MEAL_SLOT {
        varchar code PK
        varchar name
        smallint sort_order UK
    }
    GOAL_TYPE {
        varchar code PK
        varchar name
    }
    CONSENT_DOCUMENT {
        smallint id PK
        varchar consent_type "UK with version"
        varchar version
        date published_on
    }
    RECOVERY_SETTING {
        boolean id PK "single row"
        smallint kcal_deviation_threshold "200"
    }
    USER_PROFILE {
        uuid user_id PK
    }
    PROFILE_FOOD_EXCLUSION {
        uuid user_id PK
    }
    USER_CONSENT {
        uuid id PK
    }
    PLAN_ITEM {
        uuid id PK
    }
```

## Mapping to the BA data model (Fig. B.11)

| Fig. B.11 entity | Schema | Change and reason |
|---|---|---|
| User | `app_user` | + `adult_confirmed_at` (Q8), `updated_at` |
| Profile (goal, energy_target, preferences) | `user_profile`, `profile_food_exclusion` | "preferences" = permanent exclusions (Q5), stored as a relation (1NF); macro targets added for US-07 (Q3) |
| PlanningConstraint (date, available_time, equipment, excluded_foods) | `daily_constraint` + `daily_constraint_ingredient` + `daily_constraint_equipment` | multi-valued attributes moved to relations (1NF); available ingredients added (Q3) |
| DailyPlan (date, status, version) | `daily_plan` + `plan_version` | immutable versions (Q2); `status` is the derived `recovery_required` flag + active version |
| PlanItem | `plan_item` | `item_key`, meal/exercise FKs and macros added (Q3) |
| FoodEntry, ActivityEntry | `food_entry`, `activity_entry` | `input_source`, saved-meal link (US-08) |
| RecoveryProposal (status) | `recovery_proposal` | `status` not stored - derived in `v_recovery_proposal_overview` (avoids redundancy) |
| RecoveryChange (old_value, new_value, reason) | `recovery_change` | old value = FK `target_item_id`; new value = typed columns; reason category + text (US-17) |
| RecoveryDecision | `recovery_decision` | + `resulting_version_id` (link to the version created) |
| — | `deviation_event` | trigger of `recoveryRequired` (Q4, US-15) |
| — | `saved_meal`, `saved_meal_item` | US-08 (Q4) |
| — | `food_item`, `meal`, `meal_component`, `exercise`, … | local candidate catalogue (Q4, US-31, NFR-02) |
| — | `user_consent`, `consent_document` | LEG-2 (Q7) |
