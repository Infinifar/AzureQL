[CmdletBinding()]
param(
    [ValidateSet(
        "all",
        "startup",
        "tabs",
        "tasks",
        "script-directory",
        "log-1",
        "log-5",
        "log-20",
        "script-10",
        "script-50",
        "subscription"
    )]
    [string[]]$Scenario = @("all"),
    [string]$ConfigPath,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
    [string]$OutputDirectory,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$SkipPreflight,
    [switch]$PreflightOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot "benchmark\benchmark-fixtures.local.json"
} elseif (-not [IO.Path]::IsPathRooted($ConfigPath)) {
    $ConfigPath = Join-Path $repoRoot $ConfigPath
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputDirectory = Join-Path $repoRoot "artifacts\macrobenchmark\batch-$timestamp"
} elseif (-not [IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot $OutputDirectory
}

function Assert-LastExitCode([string]$Action) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE"
    }
}

function Read-FixtureConfig {
    if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
        throw "Fixture config not found: $ConfigPath. Copy benchmark-fixtures.example.json to benchmark-fixtures.local.json and fill in the real QingLong item names."
    }
    return Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
}

$fixtureConfig = $null
function Get-FixtureValue([string]$Name, [switch]$Optional) {
    if ($null -eq $script:fixtureConfig) {
        $script:fixtureConfig = Read-FixtureConfig
    }
    $property = $script:fixtureConfig.PSObject.Properties[$Name]
    $value = if ($null -eq $property) { "" } else { [string]$property.Value }
    if ([string]::IsNullOrWhiteSpace($value)) {
        if ($Optional) { return $null }
        throw "Fixture config field '$Name' is required for the selected scenario"
    }
    return $value.Trim()
}

function Get-AllFixtureArguments {
    $arguments = [ordered]@{
        "azureql.benchmark.taskCount" = Get-FixtureValue "taskCount"
        "azureql.benchmark.scriptDirectory" = Get-FixtureValue "scriptDirectory"
        "azureql.benchmark.log1MiB" = Get-FixtureValue "log1MiB"
        "azureql.benchmark.log5MiB" = Get-FixtureValue "log5MiB"
        "azureql.benchmark.log20MiB" = Get-FixtureValue "log20MiB"
        "azureql.benchmark.script10MiB" = Get-FixtureValue "script10MiB"
        "azureql.benchmark.script50MiB" = Get-FixtureValue "script50MiB"
        "azureql.benchmark.subscription" = Get-FixtureValue "subscription"
    }
    $script10Parent = Get-FixtureValue "script10MiBParent" -Optional
    $script50Parent = Get-FixtureValue "script50MiBParent" -Optional
    if ($null -ne $script10Parent) {
        $arguments["azureql.benchmark.script10MiBParent"] = $script10Parent
    }
    if ($null -ne $script50Parent) {
        $arguments["azureql.benchmark.script50MiBParent"] = $script50Parent
    }
    return $arguments
}

function New-TestCase([string]$ClassName, [System.Collections.IDictionary]$Arguments) {
    return [PSCustomObject]@{
        ClassName = $ClassName
        Arguments = $Arguments
    }
}

function Get-TestCase([string]$Name) {
    switch ($Name) {
        "startup" {
            return New-TestCase "com.autopanel.benchmark.StartupBenchmark" ([ordered]@{})
        }
        "tabs" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#switchPrimaryTabs" ([ordered]@{})
        }
        "tasks" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#scrollTaskListWith500Or1000Items" ([ordered]@{
                "azureql.benchmark.taskCount" = Get-FixtureValue "taskCount"
            })
        }
        "script-directory" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#expandAndScrollLargeScriptDirectory" ([ordered]@{
                "azureql.benchmark.scriptDirectory" = Get-FixtureValue "scriptDirectory"
            })
        }
        "log-1" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#openOneMiBLog" ([ordered]@{
                "azureql.benchmark.log1MiB" = Get-FixtureValue "log1MiB"
            })
        }
        "log-5" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#openFiveMiBLog" ([ordered]@{
                "azureql.benchmark.log5MiB" = Get-FixtureValue "log5MiB"
            })
        }
        "log-20" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#openTwentyMiBLog" ([ordered]@{
                "azureql.benchmark.log20MiB" = Get-FixtureValue "log20MiB"
            })
        }
        "script-10" {
            $arguments = [ordered]@{
                "azureql.benchmark.script10MiB" = Get-FixtureValue "script10MiB"
            }
            $parent = Get-FixtureValue "script10MiBParent" -Optional
            if ($null -ne $parent) { $arguments["azureql.benchmark.script10MiBParent"] = $parent }
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#openAndPageTenMiBScript" $arguments
        }
        "script-50" {
            $arguments = [ordered]@{
                "azureql.benchmark.script50MiB" = Get-FixtureValue "script50MiB"
            }
            $parent = Get-FixtureValue "script50MiBParent" -Optional
            if ($null -ne $parent) { $arguments["azureql.benchmark.script50MiBParent"] = $parent }
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#openAndPageFiftyMiBScript" $arguments
        }
        "subscription" {
            return New-TestCase "com.autopanel.benchmark.InteractionBenchmark#pollSubscriptionLogForSixtySeconds" ([ordered]@{
                "azureql.benchmark.subscription" = Get-FixtureValue "subscription"
            })
        }
        default { throw "Unknown scenario: $Name" }
    }
}

function Export-InstrumentationArtifacts([string]$Name) {
    # Macrobenchmark refreshes its media output for each instrumentation process. Pull after
    # every measured scenario so a later scenario cannot replace an earlier scenario's traces.
    $remoteOutput = "/sdcard/Android/media/com.autopanel.benchmark"
    $deviceOutput = Join-Path (Join-Path $OutputDirectory "device-output") $Name
    New-Item -ItemType Directory -Path $deviceOutput -Force | Out-Null
    $pullRoot = Join-Path ([IO.Path]::GetTempPath()) (
        "azureql-macrobenchmark-" + [guid]::NewGuid().ToString("N")
    )
    New-Item -ItemType Directory -Path $pullRoot -Force | Out-Null

    try {
        & $AdbPath pull $remoteOutput $pullRoot
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Scenario '$Name' finished, but its benchmark artifacts could not be pulled"
            return
        }

        $pulledOutput = Join-Path $pullRoot "com.autopanel.benchmark"
        if (-not (Test-Path -LiteralPath $pulledOutput -PathType Container)) {
            Write-Warning "Scenario '$Name' finished, but adb pull returned no benchmark output"
            return
        }

        $artifactIndex = 0
        $artifactManifest = @(
            Get-ChildItem -LiteralPath $pulledOutput -File -Recurse | ForEach-Object {
                $extension = [IO.Path]::GetExtension($_.Name)
                $compactName = "artifact-{0:D4}{1}" -f $artifactIndex, $extension
                Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $deviceOutput $compactName)
                $entry = [ordered]@{
                    localName = $compactName
                    remoteRelativePath = [IO.Path]::GetRelativePath($pulledOutput, $_.FullName)
                    sizeBytes = $_.Length
                }
                $artifactIndex++
                $entry
            }
        )
        $artifactManifest | ConvertTo-Json -Depth 4 | Set-Content `
            -LiteralPath (Join-Path $deviceOutput "artifact-manifest.json") -Encoding utf8
    } finally {
        $resolvedTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $resolvedPullRoot = [IO.Path]::GetFullPath($pullRoot)
        if (
            $resolvedPullRoot.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
            (Test-Path -LiteralPath $resolvedPullRoot -PathType Container)
        ) {
            Remove-Item -LiteralPath $resolvedPullRoot -Recurse -Force
        }
    }
}

function Invoke-Instrumentation(
    [string]$Name,
    [string]$ClassName,
    [System.Collections.IDictionary]$Arguments,
    [switch]$CaptureArtifacts
) {
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    $adbArguments = [System.Collections.Generic.List[string]]::new()
    foreach ($value in @("shell", "am", "instrument", "-w", "-r", "-e", "class", $ClassName)) {
        $adbArguments.Add($value)
    }
    foreach ($entry in $Arguments.GetEnumerator()) {
        $adbArguments.Add("-e")
        $adbArguments.Add([string]$entry.Key)
        $adbArguments.Add([string]$entry.Value)
    }
    $adbArguments.Add("com.autopanel.benchmark/androidx.test.runner.AndroidJUnitRunner")

    $adbArgumentArray = $adbArguments.ToArray()
    $lines = @(& $AdbPath @adbArgumentArray 2>&1)
    $exitCode = $LASTEXITCODE
    $lines | ForEach-Object { Write-Host $_ }
    $logPath = Join-Path $OutputDirectory "$Name-instrumentation.txt"
    $lines | Set-Content -LiteralPath $logPath -Encoding utf8
    $text = $lines -join "`n"
    if ($CaptureArtifacts) {
        Export-InstrumentationArtifacts -Name $Name
    }
    if ($exitCode -ne 0 -or $text -match "FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED" -or $text -notmatch "OK \(") {
        throw "Instrumentation scenario '$Name' failed. See $logPath"
    }
}

if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "adb not found: $AdbPath"
}

$connectedDevices = @(
    & $AdbPath devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "\tdevice$" }
)
if ($connectedDevices.Count -ne 1) {
    throw "Exactly one authorized Android device is required; found $($connectedDevices.Count)"
}
$sdkLevel = [int]((& $AdbPath shell getprop ro.build.version.sdk) -join "").Trim()
if ($sdkLevel -lt 31) {
    throw "AzureQL Macrobenchmark requires Android 12 / API 31 or newer; device API is $sdkLevel"
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
Push-Location $repoRoot
try {
    if (-not $SkipBuild) {
        Write-Host "Building benchmark APKs..." -ForegroundColor Cyan
        & ".\gradlew.bat" ":app:assembleBenchmarkRelease" ":benchmark:assembleBenchmarkRelease"
        Assert-LastExitCode "Gradle build"
    }

    if (-not $SkipInstall) {
        $appApk = Join-Path $repoRoot "app\build\outputs\apk\benchmarkRelease\app-benchmarkRelease.apk"
        $benchmarkApk = Join-Path $repoRoot "benchmark\build\outputs\apk\benchmarkRelease\benchmark-benchmarkRelease.apk"
        foreach ($apk in @($appApk, $benchmarkApk)) {
            if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw "APK not found: $apk" }
            & $AdbPath install -r -t $apk
            Assert-LastExitCode "Installing $(Split-Path $apk -Leaf)"
        }
    }

    $defaultScenarios = @(
        "startup",
        "tabs",
        "tasks",
        "script-directory",
        "log-1",
        "log-5",
        "log-20",
        "script-10",
        "script-50",
        "subscription"
    )
    $selected = if ($Scenario -contains "all") { $defaultScenarios } else { $Scenario }

    if ($PreflightOnly -or (($Scenario -contains "all") -and -not $SkipPreflight)) {
        Invoke-Instrumentation `
            -Name "preflight" `
            -ClassName "com.autopanel.benchmark.FixtureReadinessTest#verifyAllConfiguredFixtures" `
            -Arguments (Get-AllFixtureArguments)
    }

    if (-not $PreflightOnly) {
        foreach ($name in $selected) {
            $testCase = Get-TestCase $name
            Invoke-Instrumentation `
                -Name $name `
                -ClassName $testCase.ClassName `
                -Arguments $testCase.Arguments `
                -CaptureArtifacts
        }
    }

    [ordered]@{
        completedAt = (Get-Date).ToString("o")
        device = $connectedDevices[0]
        apiLevel = $sdkLevel
        scenarios = @($selected)
        preflight = $PreflightOnly -or (($Scenario -contains "all") -and -not $SkipPreflight)
        appKeptInstalled = $true
        outputDirectory = $OutputDirectory
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory "run-summary.json") -Encoding utf8

    Write-Host "`nAll selected scenarios passed." -ForegroundColor Green
    Write-Host "Artifacts: $OutputDirectory"
    Write-Host "AzureQL remains installed and its account data was not cleared."
} finally {
    Pop-Location
}
