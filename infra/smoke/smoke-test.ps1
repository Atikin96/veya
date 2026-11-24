# VEYA end-to-end smoke test over real HTTP (Windows PowerShell 5.1 / PowerShell 7).
#   powershell -ExecutionPolicy Bypass -File infra\smoke\smoke-test.ps1 [-BaseUrl http://localhost:8080] [-Db]
# Scenario UC-01: register -> login -> profile -> constraints -> food search -> plan -> diary -> deviations ->
# recovery proposal -> ACCEPT -> new version -> day summary, plus 400/401/403/404/409/422, rollback and deletion.
# No secret is stored: a random password is generated per run. -Db additionally checks persisted rows with psql in
# the local veya-postgres container (Docker Compose setup).
param([string]$BaseUrl = "http://localhost:8080", [switch]$Db)

$ErrorActionPreference = "Stop"
$script:Failures = 0
$Run = [guid]::NewGuid().ToString("N").Substring(0, 8)
$Password = [guid]::NewGuid().ToString("N")
$Date = "2026-11-16"

function Invoke-Veya([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null) {
    $headers = @{ "X-Request-Id" = "smoke-$Run-" + [guid]::NewGuid().ToString("N").Substring(0, 6) }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $p = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) { $p.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 8)); $p.ContentType = "application/json" }
    if ($PSVersionTable.PSVersion.Major -ge 7) {
        # PowerShell 7 (also on Linux CI runners): error statuses are returned, not thrown
        $r = Invoke-WebRequest @p -SkipHttpErrorCheck
        $code = [int]$r.StatusCode
        # application/problem+json is not a known text type in PowerShell 7: its content arrives as bytes
        $content = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
    } else {
        try {
            $r = Invoke-WebRequest @p
            $code = [int]$r.StatusCode; $content = $r.Content
        } catch [System.Net.WebException] {
            $resp = $_.Exception.Response
            if ($null -eq $resp) { throw }
            $code = [int]$resp.StatusCode
            $content = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        }
    }
    $json = $null
    if ($content) { try { $json = $content | ConvertFrom-Json } catch { } }
    [pscustomobject]@{ Status = $code; Json = $json }
}

function Check([string]$Step, $Response, [int]$Expected, [string]$Info = "") {
    $ok = $Response.Status -eq $Expected
    if (-not $ok) { $script:Failures++ }
    $code = if ($Response.Json -and $Response.Json.code) { " " + $Response.Json.code } else { "" }
    "{0} {1,-62} HTTP {2}{3} (expected {4}) {5}" -f ($(if ($ok) { "OK  " } else { "FAIL" })), $Step, $Response.Status, $code, $Expected, $Info
}

function Assert-True([string]$Step, [bool]$Condition, [string]$Info = "") {
    if (-not $Condition) { $script:Failures++ }
    "{0} {1,-62} {2}" -f ($(if ($Condition) { "OK  " } else { "FAIL" })), $Step, $Info
}

function Key($Plan, [string]$Slot) { ($Plan.items | Where-Object { $_.mealSlot -eq $Slot -or $_.itemType -eq $Slot } | Select-Object -First 1).itemKey }

$r = Invoke-Veya GET "/actuator/health/readiness"; Check "Readiness" $r 200 $r.Json.status

# --- identity ---------------------------------------------------------------------------------------------------
$Email = "smoke.$Run@example.org"
$reg = @{ email = $Email; password = $Password; adultConfirmed = $true; consentDocumentIds = @(1, 2) }
$r = Invoke-Veya POST "/api/v1/auth/register" $reg; Check "Register" $r 201 "userId=$($r.Json.userId)"
$UserId = $r.Json.userId
$r = Invoke-Veya POST "/api/v1/auth/register" @{ email = "teen.$Email"; password = $Password; adultConfirmed = $false; consentDocumentIds = @(1, 2) }
Check "400 register without adult confirmation" $r 400
$r = Invoke-Veya POST "/api/v1/auth/register" $reg; Check "409 duplicate e-mail" $r 409
$r = Invoke-Veya POST "/api/v1/auth/login" @{ email = $Email; password = $Password }; Check "Login" $r 200 "expiresIn=$($r.Json.expiresIn)"
$Token = $r.Json.accessToken
$r = Invoke-Veya POST "/api/v1/auth/login" @{ email = $Email; password = "definitely-wrong" }; Check "401 wrong password" $r 401
$r = Invoke-Veya GET "/api/v1/me"; Check "401 missing JWT" $r 401
$r = Invoke-Veya GET "/api/v1/me" $null "not.a.jwt"; Check "401 invalid JWT" $r 401
$r = Invoke-Veya GET "/api/v1/me" $null $Token; Check "Current account with JWT" $r 200

# --- profile, constraints, food -----------------------------------------------------------------------------------
$r = Invoke-Veya PUT "/api/v1/me/profile" @{ goal = "LOSE_WEIGHT"; energyTargetKcal = 1900; proteinTargetG = 110; fatTargetG = 60; carbsTargetG = 220; foodExclusions = @("PEANUT") } $Token
Check "Save profile" $r 200 "target=$($r.Json.energyTargetKcal)"
$r = Invoke-Veya PUT "/api/v1/me/profile" @{ goal = "LOSE_WEIGHT"; energyTargetKcal = 100; proteinTargetG = 110; fatTargetG = 60; carbsTargetG = 220; foodExclusions = @() } $Token
Check "400 profile out of range" $r 400
$r = Invoke-Veya GET "/api/v1/foods?q=chick" $null $Token
Check "Food search" $r 200 "first=$($r.Json.items[0].name) provider=$($r.Json.providerStatus)"
$r = Invoke-Veya GET "/api/v1/exercises" $null $Token; Check "Exercise catalogue" $r 200 "count=$($r.Json.Count)"
$Meals = @{}; (Invoke-Veya GET "/api/v1/meals?limit=100" $null $Token).Json | ForEach-Object { $Meals[$_.name] = $_.mealId }
$Ex = @{}; (Invoke-Veya GET "/api/v1/exercises?limit=100" $null $Token).Json | ForEach-Object { $Ex[$_.name] = $_.exerciseId }

# --- plan ---------------------------------------------------------------------------------------------------------
$r = Invoke-Veya POST "/api/v1/meal-plan-suggestions" @{ date = $Date; mealSlots = @("BREAKFAST", "LUNCH", "DINNER") } $Token
Check "Meal plan suggestion (nothing saved)" $r 200 "total=$($r.Json.totals.kcal) kcal"
$r = Invoke-Veya POST "/api/v1/plans" @{ date = "2026-11-18"; items = @(@{ itemType = "MEAL"; mealSlot = "LUNCH"; mealId = $Meals["Chicken rice bowl"] }, @{ itemType = "MEAL"; mealSlot = "SNACK"; mealId = 12 }) } $Token
Check "422 plan with an excluded meal (whole request rejected)" $r 422 "$($r.Json.blockingConstraints[0].type)"
$r = Invoke-Veya GET "/api/v1/plans?date=2026-11-18" $null $Token; Check "Rollback: no plan was stored for 2026-11-18" $r 404
$plan = @{ date = $Date; items = @(
        @{ itemType = "MEAL"; mealSlot = "BREAKFAST"; plannedTime = "08:00"; mealId = $Meals["Oatmeal with banana"] },
        @{ itemType = "MEAL"; mealSlot = "LUNCH"; plannedTime = "13:00"; mealId = $Meals["Chicken rice bowl"] },
        @{ itemType = "WORKOUT"; plannedTime = "18:00"; exerciseId = $Ex["Running, 10 km/h"]; durationMin = 30 }) }
$r = Invoke-Veya POST "/api/v1/plans" $plan $Token; Check "Create plan (version 1)" $r 201 "version=$($r.Json.activeVersion)"
$Plan = $r.Json; $PlanId = $Plan.planId
$r = Invoke-Veya GET "/api/v1/plans?date=2026-11-17&x=1" $null $Token; Check "404 no plan for another date" $r 404
$r = Invoke-Veya GET "/api/v1/plans/$([guid]::NewGuid())" $null $Token; Check "404 unknown plan id" $r 404

# --- deviation ----------------------------------------------------------------------------------------------------
$lunchKey = Key $Plan "LUNCH"; $lunchKcal = ($Plan.items | Where-Object { $_.itemKey -eq $lunchKey }).plannedKcal
$r = Invoke-Veya POST "/api/v1/food-entries" @{ date = $Date; mealSlot = "LUNCH"; inputSource = "MANUAL"; planItemKey = $lunchKey; foodName = "Office pizza (3 slices)"; portionG = 330; kcal = [math]::Round($lunchKcal + 250, 1); proteinG = 32; fatG = 38; carbsG = 110 } $Token
Check "Diary entry linked to the planned lunch" $r 201 "kcal=$($r.Json.entry.kcal)"
$r = Invoke-Veya POST "/api/v1/plans/$PlanId/items/$lunchKey/status" @{ status = "COMPLETED" } $Token
Check "Confirm lunch (+250 kcal)" $r 200 "recoveryRequired=$($r.Json.recoveryRequired) $($r.Json.newDeviations[0].type)"
$r = Invoke-Veya POST "/api/v1/plans/$PlanId/items/$lunchKey/status" @{ status = "COMPLETED" } $Token; Check "409 status recorded twice" $r 409
$r = Invoke-Veya POST "/api/v1/activity-entries" @{ date = $Date; exerciseId = $Ex["Running, 10 km/h"]; planItemKey = (Key $Plan "WORKOUT"); status = "SKIPPED"; durationMin = $null } $Token
Check "Skip the planned run" $r 201 "recoveryRequired=$($r.Json.recoveryRequired)"
$r = Invoke-Veya GET "/api/v1/days/$Date/nutrition" $null $Token; Check "Nutrition totals" $r 200 "consumed=$($r.Json.consumed.kcal) remaining=$($r.Json.remaining.kcal)"
$r = Invoke-Veya GET "/api/v1/plans/$PlanId/deviations" $null $Token; Check "Open deviations" $r 200 "$(($r.Json | ForEach-Object { $_.type }) -join ',')"

# --- recovery -----------------------------------------------------------------------------------------------------
$r = Invoke-Veya POST "/api/v1/plans/$PlanId/recovery-proposals" @{ baseVersion = 1 } $Token
Check "Recovery proposal" $r 201 "changes=$($r.Json.changes.Count) forecast=$($r.Json.totals.forecastKcal)"
$Proposal = $r.Json
if ($Proposal) { $Proposal.changes | ForEach-Object { "       - {0} [{1}] {2}" -f $_.changeType, $_.reason.category, $_.reason.text } }
$stale = (Invoke-Veya POST "/api/v1/plans/$PlanId/recovery-proposals" $null $Token).Json.proposalId
$r = Invoke-Veya POST "/api/v1/recovery-proposals/$($Proposal.proposalId)/decision" @{ decision = "ACCEPT"; baseVersion = 1 } $Token
Check "ACCEPT" $r 200 "activeVersion=$($r.Json.plan.activeVersion) reason=$($r.Json.plan.changeReason) recoveryRequired=$($r.Json.plan.recoveryRequired)"
$r = Invoke-Veya POST "/api/v1/recovery-proposals/$stale/decision" @{ decision = "ACCEPT"; baseVersion = 1 } $Token
Check "409 stale baseVersion" $r 409 "currentVersion=$($r.Json.currentVersion)"
$r = Invoke-Veya GET "/api/v1/plans/$PlanId" $null $Token
Assert-True "New active version 2, frozen lunch kept COMPLETED" ($r.Json.activeVersion -eq 2 -and (($r.Json.items | Where-Object { $_.itemKey -eq $lunchKey }).status -eq "COMPLETED"))
$r = Invoke-Veya GET "/api/v1/days/$Date/summary" $null $Token
Check "Day summary" $r 200 "activeVersion=$($r.Json.plan.activeVersion) consumed=$($r.Json.nutrition.consumedKcal) openItems=$($r.Json.openItems)"

# --- 422 no feasible recovery, 403 -------------------------------------------------------------------------------
$r = Invoke-Veya POST "/api/v1/plans" @{ date = "2026-11-19"; items = @(@{ itemType = "MEAL"; mealSlot = "DINNER"; mealId = $Meals["Tofu vegetable stir-fry"] }) } $Token
$Plan2 = $r.Json.planId
$null = Invoke-Veya PUT "/api/v1/constraints/2026-11-19" @{ availableTimeMin = 2; availableEquipment = @(); availableIngredients = @(); excludedIngredients = @() } $Token
$r = Invoke-Veya POST "/api/v1/plans/$Plan2/recovery-proposals" $null $Token
Check "422 no feasible recovery" $r 422 "$($r.Json.blockingConstraints[0].type): $($r.Json.blockingConstraints[0].message)"
$null = Invoke-Veya POST "/api/v1/auth/register" @{ email = "other.$Email"; password = $Password; adultConfirmed = $true; consentDocumentIds = @(1, 2) }
$Other = (Invoke-Veya POST "/api/v1/auth/login" @{ email = "other.$Email"; password = $Password }).Json.accessToken
$r = Invoke-Veya GET "/api/v1/plans/$PlanId" $null $Other; Check "403 another user's plan" $r 403
$r = Invoke-Veya GET "/api/v1/recovery-proposals/$($Proposal.proposalId)" $null $Other; Check "403 another user's proposal" $r 403

if ($Db) {
    $sql = "SELECT (SELECT count(*) FROM veya.plan_version pv JOIN veya.daily_plan dp ON dp.id = pv.daily_plan_id WHERE dp.id = '$PlanId') AS versions, " +
           "(SELECT decision FROM veya.recovery_decision WHERE proposal_id = '$($Proposal.proposalId)') AS decision, " +
           "(SELECT recovery_required FROM veya.daily_plan WHERE id = '$PlanId') AS recovery_required, " +
           "(SELECT count(*) FROM veya.deviation_event WHERE daily_plan_id = '$PlanId' AND resolved_at IS NULL) AS open_deviations, " +
           "(SELECT left(password_hash, 4) FROM veya.app_user WHERE id = '$UserId') AS hash_prefix"
    "DB  " + ((docker exec veya-postgres psql -U postgres -d veya -tA -F " | " -c $sql) -join "")
}

# --- account deletion ---------------------------------------------------------------------------------------------
$r = Invoke-Veya DELETE "/api/v1/me?confirm=true" $null $Token; Check "Delete account" $r 204
$r = Invoke-Veya GET "/api/v1/me" $null $Token; Check "401 token of the deleted account" $r 401
if ($Db) { "DB  rows left for deleted user: " + ((docker exec veya-postgres psql -U postgres -d veya -tA -c "SELECT count(*) FROM veya.daily_plan WHERE user_id = '$UserId'") -join "") }

""
if ($script:Failures -eq 0) { "SMOKE TEST PASSED" } else { "SMOKE TEST FAILED: $($script:Failures) check(s)"; exit 1 }
