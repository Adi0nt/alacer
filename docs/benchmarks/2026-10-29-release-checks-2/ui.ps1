# Win32 input for the rc2-ui-lifecycle client window. Usage: ui.ps1 <pid> <action> [args]...
#   click X Y   (client-area pixels)   key NAME   shot   esc
param([int]$ProcessId, [Parameter(ValueFromRemainingArguments = $true)][string[]]$Actions)
Add-Type @"
using System; using System.Runtime.InteropServices;
public struct PT { public int X; public int Y; }
public class U {
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
 [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref PT p);
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
 [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
}
"@
$keys = @{ F2 = @(0x71, 0x3C); F3 = @(0x72, 0x3D); ESC = @(0x1B, 0x01); ENTER = @(0x0D, 0x1C); SHOT = @(0x71, 0x3C) }
$h = (Get-Process -Id $ProcessId).MainWindowHandle
[U]::keybd_event(0x12, 0x38, 0, [UIntPtr]::Zero); [U]::keybd_event(0x12, 0x38, 2, [UIntPtr]::Zero)
[U]::ShowWindow($h, 9) | Out-Null
"foreground=" + [U]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 500
$i = 0
while ($i -lt $Actions.Count) {
 switch ($Actions[$i]) {
  'click' {
   $p = New-Object PT; $p.X = [int]$Actions[$i + 1]; $p.Y = [int]$Actions[$i + 2]
   [U]::ClientToScreen($h, [ref]$p) | Out-Null; [U]::SetCursorPos($p.X, $p.Y) | Out-Null
   Start-Sleep -Milliseconds 250
   [U]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 80; [U]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
   "click " + $Actions[$i + 1] + "," + $Actions[$i + 2]; $i += 3; Start-Sleep -Milliseconds 900
  }
  'wait' { Start-Sleep -Seconds ([int]$Actions[$i + 1]); $i += 2 }
  default {
   $k = $keys[$Actions[$i].ToUpper()]
   [U]::keybd_event($k[0], $k[1], 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 100
   [U]::keybd_event($k[0], $k[1], 2, [UIntPtr]::Zero); "key " + $Actions[$i]; $i += 1; Start-Sleep -Milliseconds 1200
  }
 }
}
