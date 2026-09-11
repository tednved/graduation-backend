<#
.SYNOPSIS
    校验 backend/openapi.yaml 的结构、引用、操作完整性与 §4/§7.2 规范一致性。

.DESCRIPTION
    分四步，任一步失败即以非零退出码结束：

      1. redocly lint（结构合法性 + $ref 可解析 + servers/security 定义）；
      2. redocly bundle 导出 JSON，供后续编程检查；
      3. 契约自检：operationId 唯一、每个操作显式 security、2xx/4xx 响应完整、
         204 无响应体、servers 基础路径唯一、§4 全部枚举取值一致；
      4. §7.2 错误码全集与 code→HTTP 映射一致。

    校验工具为一次性官方 CLI，通过 npx 拉取并锁定版本，不写入 pom，
    不向项目引入任何运行时依赖。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/validate-openapi.ps1
#>
[CmdletBinding()]
param(
    [string]$SpecPath
)

$ErrorActionPreference = 'Stop'
$RedoclyVersion = '2.51.2'

$failures = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$message) {
    $script:failures.Add($message) | Out-Null
    Write-Host "FAIL  $message" -ForegroundColor Red
}

function Add-Pass([string]$message) {
    Write-Host "PASS  $message" -ForegroundColor Green
}

function Resolve-ResponseRef($spec, $response) {
    # 响应可以内联，也可以 $ref 到 components.responses；后者需要解析后再检查内容。
    if (@($response.PSObject.Properties.Name) -notcontains '$ref') { return $response }
    $ref = [string]$response.'$ref'
    if ($ref -notmatch '^#/components/responses/(.+)$') { return $null }
    return $spec.components.responses.($Matches[1])
}

# ---------------------------------------------------------------------------
# 期望值：来自 项目全局规划.md §4 与 §7.2，是本脚本唯一的真值来源。
# 契约与之一致才算通过；改动契约前必须先改总纲。
# ---------------------------------------------------------------------------
$ExpectedEnums = [ordered]@{
    'UserStatus'          = @('ACTIVE', 'DISABLED')
    'UserRole'            = @('USER', 'ADMIN')
    'CertificationStatus' = @('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')
    'CertificationType'   = @('STUDENT_CARD', 'CAMPUS_EMAIL', 'MANUAL')
    'CampusStatus'        = @('ENABLED', 'DISABLED')
    'CategoryStatus'      = @('ENABLED', 'DISABLED')
    'ItemStatus'          = @('DRAFT', 'ON_SALE', 'RESERVED', 'SOLD', 'OFF_SHELF', 'DELETED')
    'ItemCondition'       = @('NEW', 'LIKE_NEW', 'GOOD', 'FAIR')
    'TradeMode'           = @('OFFLINE', 'SIMULATED_PAYMENT')
    'OrderStatus'         = @('PENDING_CONFIRMATION', 'CONFIRMED', 'PENDING_RECEIPT', 'COMPLETED', 'CANCELLED', 'REJECTED')
    'OrderAction'         = @('CREATE', 'CONFIRM', 'REJECT', 'CANCEL', 'DELIVER', 'RECEIVE')
    'ReviewStatus'        = @('VISIBLE', 'HIDDEN')
    'NotificationType'    = @('ORDER_CREATED', 'ORDER_CONFIRMED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'ORDER_DELIVERED', 'ORDER_COMPLETED', 'REVIEW_RECEIVED', 'SYSTEM')
    'FileBizType'         = @('ITEM_IMAGE', 'AVATAR', 'CERTIFICATION_EVIDENCE')
    'FileStatus'          = @('UPLOADED', 'BOUND', 'DELETED')
}

$ExpectedErrorHttp = @{
    'VALIDATION_ERROR'               = '400'
    'AUTH_INVALID_CODE'              = '401'
    'AUTH_UNAUTHORIZED'              = '401'
    'AUTH_REFRESH_INVALID'           = '401'
    'AUTH_FORBIDDEN'                 = '403'
    'USER_DISABLED'                  = '403'
    'USER_CERTIFICATION_REQUIRED'    = '403'
    'RESOURCE_NOT_FOUND'             = '404'
    'CERTIFICATION_PENDING_EXISTS'   = '409'
    'CERTIFICATION_ALREADY_REVIEWED' = '409'
    'CATEGORY_IN_USE'                = '409'
    'FILE_INVALID_TYPE'              = '400'
    'FILE_TOO_LARGE'                 = '413'
    'FILE_NOT_OWNED'                 = '403'
    'ITEM_NOT_EDITABLE'              = '409'
    'ITEM_NOT_AVAILABLE'             = '409'
    'ITEM_SELF_PURCHASE'             = '409'
    'ITEM_CONCURRENTLY_RESERVED'     = '409'
    'ITEM_SELF_OPERATION'            = '409'
    'ORDER_ILLEGAL_STATUS_TRANSITION' = '409'
    'ORDER_OPERATION_FORBIDDEN'      = '403'
    'ORDER_DUPLICATE_REQUEST'        = '409'
    'REVIEW_NOT_ALLOWED'             = '409'
    'REVIEW_ALREADY_EXISTS'          = '409'
    'INTERNAL_ERROR'                 = '500'
}

$ExpectedBasePath = '/api/v1'
$HttpMethods = @('get', 'post', 'put', 'patch', 'delete')

# ---------------------------------------------------------------------------
# 定位仓库与契约文件
# ---------------------------------------------------------------------------
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $SpecPath) {
    $SpecPath = Join-Path $repoRoot 'openapi.yaml'
}
if (-not (Test-Path -LiteralPath $SpecPath)) {
    Write-Host "找不到契约文件：$SpecPath" -ForegroundColor Red
    exit 1
}
$SpecPath = (Resolve-Path -LiteralPath $SpecPath).Path

Write-Host "契约文件：$SpecPath"
Write-Host "校验工具：@redocly/cli@$RedoclyVersion（npx，一次性）"
Write-Host ""

try {
    # -----------------------------------------------------------------------
    # 1. 结构与引用校验
    # -----------------------------------------------------------------------
    Write-Host '== 1/4 redocly lint：结构合法性 + $ref 可解析' -ForegroundColor Cyan
    & npx --yes "@redocly/cli@$RedoclyVersion" lint $SpecPath --extends=minimal
    if ($LASTEXITCODE -ne 0) {
        Add-Failure "redocly lint 未通过（退出码 $LASTEXITCODE），文档不是合法的 OpenAPI 3.1"
    }
    else {
        Add-Pass 'redocly lint 通过：YAML 可解析、OpenAPI 结构合法、全部 $ref 可解析'
    }

    if ($failures.Count -gt 0) {
        throw 'lint 未通过，后续检查无法在不可信的文档上进行'
    }

    # -----------------------------------------------------------------------
    # 2. 导出 JSON 供编程检查
    # -----------------------------------------------------------------------
    Write-Host ''
    Write-Host '== 2/4 redocly bundle：导出 JSON 供编程检查' -ForegroundColor Cyan
    $bundlePath = Join-Path ([System.IO.Path]::GetTempPath()) ("openapi-bundle-" + [guid]::NewGuid().ToString('N') + '.json')
    & npx --yes "@redocly/cli@$RedoclyVersion" bundle $SpecPath "--output=$bundlePath" --ext=json
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $bundlePath)) {
        Add-Failure "redocly bundle 未产出 JSON（退出码 $LASTEXITCODE）"
        throw 'bundle 失败'
    }
    $spec = Get-Content -LiteralPath $bundlePath -Raw -Encoding UTF8 | ConvertFrom-Json
    Add-Pass 'bundle 导出成功'

    # 收集全部操作
    $operations = @()
    foreach ($pathProp in $spec.paths.PSObject.Properties) {
        foreach ($methodProp in $pathProp.Value.PSObject.Properties) {
            if ($HttpMethods -notcontains $methodProp.Name) { continue }
            $operations += [pscustomobject]@{
                Path   = $pathProp.Name
                Method = $methodProp.Name.ToUpperInvariant()
                Op     = $methodProp.Value
            }
        }
    }

    # -----------------------------------------------------------------------
    # 3. 契约自检
    # -----------------------------------------------------------------------
    Write-Host ''
    Write-Host '== 3/4 契约自检' -ForegroundColor Cyan

    # 3.1 servers 基础路径
    $serverUrls = @($spec.servers | ForEach-Object { $_.url })
    if ($serverUrls.Count -ne 1) {
        Add-Failure "servers 必须且只能有一个条目，实际 $($serverUrls.Count) 个"
    }
    elseif ($serverUrls[0] -ne $ExpectedBasePath) {
        Add-Failure "servers[0].url 必须是 $ExpectedBasePath，实际 $($serverUrls[0])"
    }
    else {
        Add-Pass "servers 基础路径为 $ExpectedBasePath"
    }
    foreach ($pathProp in $spec.paths.PSObject.Properties) {
        if ($pathProp.Name -like "$ExpectedBasePath/*") {
            Add-Failure "路径 $($pathProp.Name) 重复了 servers 中的基础路径前缀"
        }
    }

    # 3.2 securityScheme
    if (-not $spec.components.securitySchemes.bearerAuth) {
        Add-Failure 'components.securitySchemes 缺少 bearerAuth'
    }
    elseif ($spec.components.securitySchemes.bearerAuth.scheme -ne 'bearer') {
        Add-Failure 'bearerAuth.scheme 必须是 bearer'
    }
    else {
        Add-Pass 'securityScheme bearerAuth 已定义'
    }

    # 3.3 每个操作：operationId 唯一、显式 security、2xx/4xx 响应完整
    $seenIds = @{}
    foreach ($entry in $operations) {
        $where = "$($entry.Method) $($entry.Path)"
        $op = $entry.Op
        $opProps = @($op.PSObject.Properties.Name)

        # operationId
        if ($opProps -notcontains 'operationId' -or [string]::IsNullOrWhiteSpace($op.operationId)) {
            Add-Failure "$where 缺少 operationId"
        }
        elseif ($seenIds.ContainsKey($op.operationId)) {
            Add-Failure "$where 的 operationId 与前面的操作重复：$($op.operationId)"
        }
        else {
            $seenIds[$op.operationId] = $where
        }

        # 显式 security（不依赖根级默认值）
        $requiresBearer = $false
        if ($opProps -notcontains 'security') {
            Add-Failure "$where 未显式声明 security（匿名接口应写 security: []）"
        }
        else {
            foreach ($requirement in @($op.security)) {
                foreach ($schemeName in @($requirement.PSObject.Properties | ForEach-Object { $_.Name })) {
                    if ($schemeName -ne 'bearerAuth') {
                        Add-Failure "$where 引用了未定义的 securityScheme：$schemeName"
                    }
                    else {
                        $requiresBearer = $true
                    }
                }
            }
        }

        # 响应完整
        if ($opProps -notcontains 'responses') {
            Add-Failure "$where 缺少 responses"
            continue
        }
        $codes = @($op.responses.PSObject.Properties.Name)
        if (@($codes | Where-Object { $_ -match '^2' }).Count -eq 0) {
            Add-Failure "$where 没有 2xx 成功响应"
        }
        if (@($codes | Where-Object { $_ -notmatch '^2' }).Count -eq 0) {
            Add-Failure "$where 没有任何失败响应"
        }
        if ($requiresBearer -and $codes -notcontains '401') {
            Add-Failure "$where 需要 Bearer Token，但未声明 401 失败响应"
        }
        if ($requiresBearer -and $codes -notcontains '403') {
            Add-Failure "$where 需要 Bearer Token，但未声明 403 失败响应"
        }
        foreach ($codeProp in $op.responses.PSObject.Properties) {
            $response = Resolve-ResponseRef $spec $codeProp.Value
            if ($null -eq $response) {
                Add-Failure "$where 的 $($codeProp.Name) 响应是无效或无法解析的 `$ref"
                continue
            }
            $responseProps = @($response.PSObject.Properties.Name)
            if ($responseProps -notcontains 'description') {
                Add-Failure "$where 的 $($codeProp.Name) 响应缺少 description"
            }
            if ($codeProp.Name -eq '204' -and $responseProps -contains 'content') {
                Add-Failure "$where 的 204 响应不得带响应体"
            }
            if ($codeProp.Name -ne '204' -and $responseProps -notcontains 'content') {
                Add-Failure "$where 的 $($codeProp.Name) 响应缺少 content"
            }
            if ($codeProp.Name -notmatch '^2' -and $responseProps -notcontains 'x-error-codes') {
                Add-Failure "$where 的 $($codeProp.Name) 失败响应缺少 x-error-codes"
            }
        }
    }
    if ($failures.Count -eq 0) {
        Add-Pass "操作完整性检查通过：共 $($operations.Count) 个操作，operationId 唯一，security 与响应结构齐备"
    }

    # 3.4 §4 枚举取值
    $schemas = $spec.components.schemas
    $enumOk = $true
    foreach ($enumName in $ExpectedEnums.Keys) {
        $schema = $schemas.$enumName
        if (-not $schema -or -not $schema.enum) {
            Add-Failure "缺少 §4 枚举 schema：$enumName"
            $enumOk = $false
            continue
        }
        $actual = @($schema.enum | ForEach-Object { [string]$_ })
        $expected = @($ExpectedEnums[$enumName])
        $diff = Compare-Object $actual $expected
        if ($actual.Count -ne $expected.Count -or $diff) {
            Add-Failure "$enumName 取值与 §4 不一致：契约 [$($actual -join ', ')]，§4 [$($expected -join ', ')]"
            $enumOk = $false
        }
    }
    if ($enumOk) {
        Add-Pass "§4 全部 $($ExpectedEnums.Count) 个枚举取值一致"
    }

    # -----------------------------------------------------------------------
    # 4. §7.2 错误码与 HTTP 映射
    # -----------------------------------------------------------------------
    Write-Host ''
    Write-Host '== 4/4 §7.2 错误码与 HTTP 映射' -ForegroundColor Cyan

    # ErrorCode 枚举必须等于 §7.2 全集
    $errorCodeSchema = $schemas.ErrorCode
    if (-not $errorCodeSchema -or -not $errorCodeSchema.enum) {
        Add-Failure '缺少 components.schemas.ErrorCode 或其 enum'
    }
    else {
        $declaredCodes = @($errorCodeSchema.enum | ForEach-Object { [string]$_ })
        $expectedCodes = @($ExpectedErrorHttp.Keys)
        $diff = Compare-Object $declaredCodes $expectedCodes
        if ($declaredCodes.Count -ne $expectedCodes.Count -or $diff) {
            Add-Failure "ErrorCode 枚举与 §7.2 不一致：契约 $(($declaredCodes | Sort-Object) -join ', ')"
        }
        else {
            Add-Pass "ErrorCode 枚举与 §7.2 全集一致（$($expectedCodes.Count) 个错误码）"
        }
    }

    # 每个 ErrorNNN 响应组件声明的错误码必须映射到该 HTTP 状态
    $covered = @{}
    foreach ($responseProp in $spec.components.responses.PSObject.Properties) {
        $key = $responseProp.Name
        if ($key -notmatch '^Error(\d{3})$') { continue }
        $http = $Matches[1]
        $codes = @($responseProp.Value.'x-error-codes')
        if ($codes.Count -eq 0) {
            Add-Failure "响应组件 $key 缺少 x-error-codes"
            continue
        }
        foreach ($code in $codes) {
            $code = [string]$code
            if (-not $ExpectedErrorHttp.ContainsKey($code)) {
                Add-Failure "响应组件 $key 声明了 §7.2 之外的错误码：$code"
                continue
            }
            if ($ExpectedErrorHttp[$code] -ne $http) {
                Add-Failure "错误码 $code 在 §7.2 中映射 HTTP $($ExpectedErrorHttp[$code])，却在 $key 中被声明为 $http"
            }
            $covered[$code] = $true
        }
    }
    foreach ($code in $ExpectedErrorHttp.Keys) {
        if (-not $covered.ContainsKey($code)) {
            Add-Failure "§7.2 错误码 $code 未出现在任何错误响应组件的 x-error-codes 中"
        }
    }
    if ($failures.Count -eq 0) {
        Add-Pass '全部错误码的 HTTP 映射一致，且无遗漏、无多余'
    }

    Remove-Item -LiteralPath $bundlePath -ErrorAction SilentlyContinue
}
catch {
    Remove-Item -LiteralPath $bundlePath -ErrorAction SilentlyContinue
    Add-Failure "校验中断：$($_.Exception.Message)"
}

Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host "$($failures.Count) 项校验失败：" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host "  - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "openapi.yaml 校验通过：$($operations.Count) 个操作，结构、引用、枚举与错误码全部一致。" -ForegroundColor Green
exit 0
