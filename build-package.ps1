$base = 'C:\MCSManager\daemon\data\InstanceData\FreeCore-Survival\libraries'
$cp = [string]::Join(';', (Get-ChildItem -LiteralPath $base -Recurse -Filter '*.jar' -File | Select-Object -ExpandProperty FullName))
$out = Join-Path (Resolve-Path '.') 'build\manual-classes-fixed'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item $out -ItemType Directory -Force | Out-Null
$sources = Get-ChildItem 'src\main\java' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& javac --release 25 -encoding UTF-8 -cp $cp -d $out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item 'src\main\resources\config.yml' $out -Force
New-Item (Join-Path $out 'lang') -ItemType Directory -Force | Out-Null
Copy-Item 'src\main\resources\lang\zh_CN.yml' (Join-Path $out 'lang') -Force
$descriptor = @'
name: FreeCoreItem
version: 1.0.21
main: io.github.freecoreitem.FreeCoreItemPlugin
api-version: '26.2'
authors: [FreeCore]
description: Lightweight custom item editor and runtime for FreeCore
commands:
  fitem:
    description: Manage FreeCoreItem custom items
    usage: /fitem help
    aliases: [freecoreitem]
permissions:
  freecoreitem.use:
    default: true
  freecoreitem.admin:
    default: op
    children:
      freecoreitem.create: true
      freecoreitem.delete: true
      freecoreitem.edit: true
      freecoreitem.attribute: true
      freecoreitem.give: true
      freecoreitem.reload: true
  freecoreitem.create:
    default: op
  freecoreitem.delete:
    default: op
  freecoreitem.edit:
    default: op
  freecoreitem.attribute:
    default: op
  freecoreitem.give:
    default: op
  freecoreitem.reload:
    default: op
  freecoreitem.cooldown.bypass:
    default: op
'@
[IO.File]::WriteAllText((Join-Path $out 'plugin.yml'), $descriptor, (New-Object Text.UTF8Encoding($false)))
$jar = Join-Path (Resolve-Path '.') 'build\libs\FreeCoreItem-1.0.21.jar'
New-Item 'build\libs' -ItemType Directory -Force | Out-Null
if (Test-Path $jar) { Remove-Item $jar -Force }
& 'C:\Program Files\Java\jdk-25.0.4\bin\jar.exe' --create --file $jar -C $out .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Remove old versions from staging before copying the newest jar
$stageDirs = @('C:\FreeCoreStaging\Survival','C:\FreeCoreStaging\Tech')
foreach ($sd in $stageDirs) {
    if (Test-Path $sd) {
        Get-ChildItem -Path $sd -Filter 'FreeCoreItem*.jar' | Remove-Item -Force
        Copy-Item $jar (Join-Path $sd 'FreeCoreItem-1.0.21.jar') -Force
    }
}
$live = @('C:\MCSManager\daemon\data\InstanceData\FreeCore-Survival\plugins\FreeCoreItem.jar','C:\MCSManager\daemon\data\InstanceData\FreeCore-Tech\plugins\FreeCoreItem.jar')
foreach ($p in $live) { Copy-Item $jar $p -Force }
$liveLang = @('C:\MCSManager\daemon\data\InstanceData\FreeCore-Survival\plugins\FreeCoreItem\lang\zh_CN.yml','C:\MCSManager\daemon\data\InstanceData\FreeCore-Tech\plugins\FreeCoreItem\lang\zh_CN.yml')
foreach ($lp in $liveLang) {
    if (Test-Path (Split-Path $lp)) {
        Copy-Item 'src\main\resources\lang\zh_CN.yml' $lp -Force
    }
}
& 'C:\Program Files\Java\jdk-25.0.4\bin\jar.exe' --extract --file $jar plugin.yml
Get-Content plugin.yml -Raw
Remove-Item plugin.yml -Force
Get-FileHash $jar -Algorithm SHA256
