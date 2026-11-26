param([switch] $SelfTest)

function Get-JavaFault([xml] $EventXml) {
    $data = @{}
    foreach ($field in $EventXml.Event.EventData.Data) {
        $data[$field.GetAttribute('Name')] = $field.InnerText
    }
    if ($data.AppName -in @('java.exe', 'javaw.exe')) {
        return ("{0} faulted in {1} {2}, exception {3}" -f $data.AppName,
            $data.ModuleName, $data.ModuleVersion, $data.ExceptionCode)
    }
}

if ($SelfTest) {
    [xml]$sample = @'
<Event><EventData>
  <Data Name="AppName">java.exe</Data><Data Name="ModuleName">sample.dll</Data>
  <Data Name="ModuleVersion">1.2</Data><Data Name="ExceptionCode">test-code</Data>
</EventData></Event>
'@
    if ((Get-JavaFault $sample) -ne 'java.exe faulted in sample.dll 1.2, exception test-code') {
        throw 'Could not read the fault fields of a Java event.'
    }
    $sample.Event.EventData.Data[0].InnerText = 'unrelated.exe'
    if (Get-JavaFault $sample) { throw 'Reported an unrelated application event.' }
    return
}

$start = (Get-Item -LiteralPath $env:GITHUB_WORKSPACE).CreationTime
try {
    $events = Get-WinEvent -FilterHashtable @{
        LogName = 'Application'
        ProviderName = 'Application Error'
        Id = 1000
        StartTime = $start
    } -ErrorAction Stop
} catch {
    if ($_.FullyQualifiedErrorId -notlike 'NoMatchingEventsFound*') { throw }
    $events = @()
}
$count = 0
foreach ($event in $events) {
    $fault = Get-JavaFault ([xml]$event.ToXml())
    if ($fault) {
        $count++
        Write-Output ("{0:u} {1}" -f $event.TimeCreated, $fault)
    }
}
if ($count -eq 0) { Write-Output 'Windows recorded no Java Application Error event during this job.' }
