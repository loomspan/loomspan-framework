[CmdletBinding()]
param(
    [string] $BuildDirectory = [IO.Path]::Combine([IO.Path]::GetTempPath(), "loomspan-jslt-$([Guid]::NewGuid().ToString('N'))"),
    [string] $MavenRepository
)

$ErrorActionPreference = 'Stop'
$commit = 'cf30e6d9be247d489dac6bf53bf930322c8fd663'
$upstream = 'https://github.com/schibsted/jslt.git'
$buildPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($BuildDirectory)
$pom = Join-Path $PSScriptRoot '../../third-party/jslt/pom.xml'
$mavenOptions = @()
if ($MavenRepository) {
    $repositoryPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($MavenRepository)
    $mavenOptions += "-Dmaven.repo.local=$repositoryPath"
}

function Invoke-BuildCommand {
    param([string] $Program, [string[]] $Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Program exited with code $LASTEXITCODE. Build directory: $buildPath"
    }
}

if (Test-Path -LiteralPath $buildPath) {
    throw "Build directory must be a new path: $buildPath"
}
New-Item -ItemType Directory -Path $buildPath | Out-Null
Write-Host "Building JSLT from $commit in $buildPath"
Push-Location -LiteralPath $buildPath
try {
    Invoke-BuildCommand git @('init', '--quiet')
    Invoke-BuildCommand git @('remote', 'add', 'origin', $upstream)
    Invoke-BuildCommand git @('fetch', '--depth=1', 'origin', $commit)
    Invoke-BuildCommand git @('checkout', '--quiet', '--detach', 'FETCH_HEAD')
    $head = Invoke-BuildCommand git @('rev-parse', 'HEAD')
    if ($head -cne $commit) {
        throw "Expected JSLT commit $commit but checked out $head"
    }
    $wrapper = if ($IsWindows) { './gradlew.bat' } else { './gradlew' }
    Invoke-BuildCommand $wrapper @('--no-daemon', ':core:test', ':core:jar')
    $jar = Resolve-Path -LiteralPath 'core/build/libs/core-0.1.15.jar'
}
finally {
    Pop-Location
}

Invoke-BuildCommand mvn ($mavenOptions + @(
    'org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file',
    "-Dfile=$jar",
    "-DpomFile=$pom"
))
