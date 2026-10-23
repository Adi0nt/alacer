#!/usr/bin/env python3
"""Session 31: operating-system probes for the A/A pilot runner and its monitor (standard library only).

Windows 11 is the reference platform (ctypes: Toolhelp32, GetProcessTimes, GetSystemTimes,
NtQueryInformationProcess, GetLogicalProcessorInformationEx, PDH, powrprof, user32). Linux (/proc, /sys) is
supported so the tooling can be exercised in a container; nothing measured there judges frame times.

Every probe returns a Reading: {'status': 'ok' | 'unavailable' | 'error', 'value': ..., 'source': ..., 'reason': ...}.
A probe that cannot answer says so; it never substitutes a normal-looking value. Window titles and window
contents are never read: the foreground probe reports only the owning process's executable name.
"""
import ctypes
import os
from pathlib import Path
import struct
import subprocess
import sys
import time

WINDOWS = sys.platform == 'win32'
CLIENT_MARKERS = ('KnotClient', 'devlaunchinjector.Main')
GRADLE_MARKERS = ('GradleDaemon', 'GradleWrapperMain', 'org.gradle.launcher')
JAVA_NAMES = ('java.exe', 'javaw.exe', 'java')

# Well-known Windows power scheme GUIDs (the friendly name is read from the system when possible).
POWER_SCHEMES = {
    '8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c': 'High performance',
    '381b4222-f694-41f0-9685-ff5bb260df2e': 'Balanced',
    'a1841308-3541-4fab-bc81-f71556f20b4a': 'Power saver',
    'e9a42b02-d5df-448d-aa00-03f14749eb61': 'Ultimate Performance',
}


def ok(value, source):
    return {'status': 'ok', 'value': value, 'source': source}


def unavailable(reason, source=None):
    return {'status': 'unavailable', 'value': None, 'source': source, 'reason': reason}


def error(exc, source=None):
    return {'status': 'error', 'value': None, 'source': source, 'reason': f'{type(exc).__name__}: {exc}'}


def is_client_command(cmd):
    return bool(cmd) and any(m in cmd for m in CLIENT_MARKERS) and not any(m in cmd for m in GRADLE_MARKERS)


def is_gradle_command(cmd):
    return bool(cmd) and any(m in cmd for m in GRADLE_MARKERS)


def mask_to_processors(mask):
    return [i for i in range(64) if mask >> i & 1]


def processors_to_mask(processors):
    mask = 0
    for p in processors:
        if not 0 <= p < 64:
            raise ValueError('processor outside group 0: %r' % p)
        mask |= 1 << p
    return mask


# ---------------------------------------------------------------------------------------------------------------
# Pure parsers (unit-tested with synthetic buffers on any platform).

def parse_processor_core_relations(buf):
    """SYSTEM_LOGICAL_PROCESSOR_INFORMATION_EX records (RelationProcessorCore) to a list of cores.

    Layout per record: DWORD Relationship, DWORD Size, then PROCESSOR_RELATIONSHIP: BYTE Flags, BYTE EfficiencyClass,
    BYTE Reserved[20], WORD GroupCount, GROUP_AFFINITY GroupMask[GroupCount] at offset 32 (KAFFINITY Mask, WORD Group,
    WORD Reserved[3]; 16 bytes each).
    """
    cores = []
    offset = 0
    while offset + 8 <= len(buf):
        relationship, size = struct.unpack_from('<II', buf, offset)
        if size == 0:
            raise ValueError('zero-sized record')
        if relationship == 0:
            flags, efficiency = struct.unpack_from('<BB', buf, offset + 8)
            (group_count,) = struct.unpack_from('<H', buf, offset + 30)
            groups = []
            for g in range(group_count):
                mask, group = struct.unpack_from('<QH', buf, offset + 32 + 16 * g)
                groups.append((group, mask))
            cores.append({'smt': bool(flags & 1), 'efficiency_class': efficiency, 'groups': groups})
        offset += size
    return cores


def topology_from_cores(cores, source):
    """Logical processor -> efficiency class. Higher classes are more performant (Windows' definition)."""
    classes = {}
    smt = {}
    multi_group = False
    for core in cores:
        for group, mask in core['groups']:
            if group != 0:
                multi_group = True
            for p in mask_to_processors(mask):
                index = group * 64 + p
                classes[index] = core['efficiency_class']
                smt[index] = core['smt']
    distinct = sorted(set(classes.values()))
    value = {'logical_processors': len(classes), 'cores': len(cores), 'efficiency_class': classes,
             'smt': smt, 'classes': distinct, 'hybrid': len(distinct) > 1, 'multi_group': multi_group}
    if len(distinct) > 1:
        top = distinct[-1]
        value['performance_processors'] = sorted(p for p, c in classes.items() if c == top)
        value['efficiency_processors'] = sorted(p for p, c in classes.items() if c != top)
    return ok(value, source)


def parse_unicode_string_buffer(buf, pointer_size=8):
    """The UNICODE_STRING (Length, MaximumLength, Buffer) that NtQueryInformationProcess(60) writes, followed by
    its characters in the same buffer; returns the string from the bytes after the header."""
    (length,) = struct.unpack_from('<H', buf, 0)
    header = 4 + (4 if pointer_size == 8 else 0) + pointer_size
    return bytes(buf[header:header + length]).decode('utf-16-le', 'replace')


def pdh_instance_processor(name):
    """'0,7' -> 7, '1,3' -> 67; totals ('_Total', '0,_Total') and anything else -> None."""
    parts = name.split(',')
    if len(parts) != 2 or not parts[0].isdigit() or not parts[1].isdigit():
        return None
    return int(parts[0]) * 64 + int(parts[1])


def parse_cpu_list(text):
    """Linux CPU list '0-3,8,10-11' -> [0, 1, 2, 3, 8, 10, 11]."""
    out = []
    for part in text.strip().split(','):
        if not part:
            continue
        if '-' in part:
            a, b = part.split('-')
            out += range(int(a), int(b) + 1)
        else:
            out.append(int(part))
    return out


# ---------------------------------------------------------------------------------------------------------------

class Platform:
    """Interface; see WindowsPlatform and LinuxPlatform."""
    name = 'unknown'

    def processes(self):
        """{(pid, start_token): {'pid', 'ppid', 'name', 'cpu_s'}} for every process the probe can see."""
        raise NotImplementedError

    def system_times(self):
        """(idle_s, total_s) summed over all logical processors since boot."""
        raise NotImplementedError

    def command_line(self, pid):
        raise NotImplementedError

    def executable(self, pid):
        raise NotImplementedError

    def topology(self):
        raise NotImplementedError

    def power_plan(self):
        raise NotImplementedError

    def foreground(self):
        raise NotImplementedError

    def affinity(self, pid):
        raise NotImplementedError

    def set_affinity(self, pid, processors):
        raise NotImplementedError

    def priority(self, pid):
        return unavailable('not implemented on ' + self.name)

    def frequency(self, topology):
        """Effective clock in MHz (total and per core class), or unavailable."""
        raise NotImplementedError

    def memory(self):
        raise NotImplementedError

    def static_info(self):
        raise NotImplementedError

    def close(self):
        pass

    def keep_awake(self):
        """Keep the machine from sleeping and the display on while a stage runs (released at process exit)."""
        return unavailable('not implemented on ' + self.name)

    def find_clients(self, procs=None):
        found = []
        for (pid, _), p in (self.processes() if procs is None else procs).items():
            if p['name'].lower() in JAVA_NAMES:
                cmd = self.command_line(pid)
                if not cmd:
                    if any(key[0] == pid for key in self.processes()):
                        raise OSError(f'cannot classify Java process {pid}: command line unavailable')
                    continue  # The process really exited after the first snapshot.
                if is_client_command(cmd):
                    found.append({'pid': pid, 'exe': self.executable(pid), 'cmdline': cmd})
        return found


# ---------------------------------------------------------------------------------------------------------------

class LinuxPlatform(Platform):
    name = 'linux'

    def __init__(self):
        self.tick = os.sysconf('SC_CLK_TCK')

    def processes(self):
        out = {}
        for d in Path('/proc').iterdir():
            if not d.name.isdigit():
                continue
            try:
                stat = (d / 'stat').read_text()
            except OSError:
                continue
            # The command name is in parentheses and may contain spaces.
            close = stat.rindex(')')
            name = stat[stat.index('(') + 1:close]
            fields = stat[close + 2:].split()
            ppid = int(fields[1])
            cpu = (int(fields[11]) + int(fields[12])) / self.tick
            start = int(fields[19])
            out[(int(d.name), start)] = {'pid': int(d.name), 'ppid': ppid, 'name': name, 'cpu_s': cpu}
        return out

    def system_times(self):
        fields = [int(x) for x in Path('/proc/stat').read_text().splitlines()[0].split()[1:]]
        idle = fields[3] + (fields[4] if len(fields) > 4 else 0)
        return idle / self.tick, sum(fields[:8]) / self.tick

    def command_line(self, pid):
        try:
            return ' '.join(p.decode('utf-8', 'replace') for p in Path(f'/proc/{pid}/cmdline').read_bytes().split(b'\0') if p)
        except OSError:
            return None

    def executable(self, pid):
        try:
            return os.readlink(f'/proc/{pid}/exe')
        except OSError:
            return None

    def topology(self):
        n = os.cpu_count() or 0
        core = Path('/sys/devices/cpu_core/cpus')
        atom = Path('/sys/devices/cpu_atom/cpus')
        if core.exists() and atom.exists():
            p = parse_cpu_list(core.read_text())
            e = parse_cpu_list(atom.read_text())
            cores = [{'smt': False, 'efficiency_class': 1, 'groups': [(0, processors_to_mask([x]))]} for x in p]
            cores += [{'smt': False, 'efficiency_class': 0, 'groups': [(0, processors_to_mask([x]))]} for x in e]
            return topology_from_cores(cores, 'linux:/sys/devices/cpu_core,cpu_atom')
        caps = {}
        for i in range(n):
            f = Path(f'/sys/devices/system/cpu/cpu{i}/cpu_capacity')
            if f.exists():
                caps[i] = int(f.read_text())
        if caps and len(caps) == n:
            levels = sorted(set(caps.values()))
            cores = [{'smt': False, 'efficiency_class': levels.index(c), 'groups': [(0, 1 << i)]} for i, c in caps.items()]
            return topology_from_cores(cores, 'linux:cpu_capacity')
        return unavailable(f'{n} logical processors; no core-type information exposed (not a hybrid topology '
                           'report)', 'linux:/sys')

    def power_plan(self):
        profile = Path('/sys/firmware/acpi/platform_profile')
        governor = Path('/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor')
        if profile.exists():
            name = profile.read_text().strip()
            return ok({'guid': None, 'name': name}, 'linux:platform_profile')
        if governor.exists():
            name = governor.read_text().strip()
            return ok({'guid': None, 'name': 'governor:' + name}, 'linux:scaling_governor')
        return unavailable('no platform profile or cpufreq governor exposed', 'linux:/sys')

    def foreground(self):
        return unavailable('foreground window is not probed on Linux', 'linux')

    def affinity(self, pid):
        try:
            return ok(sorted(os.sched_getaffinity(pid)), 'linux:sched_getaffinity')
        except OSError as e:
            return error(e, 'linux:sched_getaffinity')

    def set_affinity(self, pid, processors):
        os.sched_setaffinity(pid, processors)
        # Threads created before the call keep their own masks on Linux; apply to every thread too.
        for t in Path(f'/proc/{pid}/task').iterdir():
            try:
                os.sched_setaffinity(int(t.name), processors)
            except OSError:
                pass

    def priority(self, pid):
        try:
            return ok({'nice': os.getpriority(os.PRIO_PROCESS, pid)}, 'linux:getpriority')
        except OSError as e:
            return error(e, 'linux:getpriority')

    def frequency(self, topology):
        n = os.cpu_count() or 0
        mhz = {}
        for i in range(n):
            f = Path(f'/sys/devices/system/cpu/cpu{i}/cpufreq/scaling_cur_freq')
            if f.exists():
                try:
                    mhz[i] = int(f.read_text()) / 1000.0
                except (OSError, ValueError):
                    pass
        if not mhz:
            return unavailable('no cpufreq scaling_cur_freq exposed (virtual machine or container)', 'linux:cpufreq')
        return ok(summarize_frequency(mhz, None, topology), 'linux:scaling_cur_freq')

    def memory(self):
        for line in Path('/proc/meminfo').read_text().splitlines():
            if line.startswith('MemAvailable:'):
                return ok({'available_mb': int(line.split()[1]) / 1024.0}, 'linux:/proc/meminfo')
        return unavailable('MemAvailable missing', 'linux:/proc/meminfo')

    def static_info(self):
        model = None
        for line in Path('/proc/cpuinfo').read_text().splitlines():
            if line.startswith('model name'):
                model = line.split(':', 1)[1].strip()
                break
        return {'platform': 'linux', 'os': ' '.join(os.uname()), 'cpu_model': model,
                'logical_processors': os.cpu_count()}


def summarize_frequency(per_processor_mhz, nominal_mhz, topology):
    value = {'mean_mhz': sum(per_processor_mhz.values()) / len(per_processor_mhz), 'nominal_mhz': nominal_mhz,
             'per_processor_mhz': per_processor_mhz}
    topo = topology.get('value') if topology and topology.get('status') == 'ok' else None
    if topo and topo.get('hybrid'):
        for key, procs in (('p_mean_mhz', topo['performance_processors']), ('e_mean_mhz', topo['efficiency_processors'])):
            vals = [per_processor_mhz[p] for p in procs if p in per_processor_mhz]
            value[key] = sum(vals) / len(vals) if vals else None
    return value


# ---------------------------------------------------------------------------------------------------------------

if WINDOWS:
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL('kernel32', use_last_error=True)
    ntdll = ctypes.WinDLL('ntdll')
    user32 = ctypes.WinDLL('user32', use_last_error=True)
    PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    PROCESS_SET_INFORMATION = 0x0200
    TH32CS_SNAPPROCESS = 0x2
    INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value

    class FILETIME(ctypes.Structure):
        _fields_ = [('lo', wintypes.DWORD), ('hi', wintypes.DWORD)]

        def seconds(self):
            return ((self.hi << 32) | self.lo) / 1e7

    class PROCESSENTRY32W(ctypes.Structure):
        _fields_ = [('dwSize', wintypes.DWORD), ('cntUsage', wintypes.DWORD), ('th32ProcessID', wintypes.DWORD),
                    ('th32DefaultHeapID', ctypes.c_size_t), ('th32ModuleID', wintypes.DWORD),
                    ('cntThreads', wintypes.DWORD), ('th32ParentProcessID', wintypes.DWORD),
                    ('pcPriClassBase', ctypes.c_long), ('dwFlags', wintypes.DWORD),
                    ('szExeFile', ctypes.c_wchar * 260)]

    class GUID(ctypes.Structure):
        _fields_ = [('Data1', wintypes.DWORD), ('Data2', wintypes.WORD), ('Data3', wintypes.WORD),
                    ('Data4', ctypes.c_ubyte * 8)]

        def text(self):
            d4 = bytes(self.Data4)
            return '%08x-%04x-%04x-%s-%s' % (self.Data1, self.Data2, self.Data3, d4[:2].hex(), d4[2:].hex())

    class PDH_FMT_COUNTERVALUE(ctypes.Structure):
        _fields_ = [('CStatus', wintypes.DWORD), ('doubleValue', ctypes.c_double)]

    class PDH_FMT_COUNTERVALUE_ITEM_W(ctypes.Structure):
        _fields_ = [('szName', ctypes.c_wchar_p), ('FmtValue', PDH_FMT_COUNTERVALUE)]

    kernel32.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    kernel32.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
    kernel32.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    kernel32.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    kernel32.OpenProcess.restype = wintypes.HANDLE
    kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel32.LocalFree.argtypes = [ctypes.c_void_p]
    kernel32.LocalFree.restype = ctypes.c_void_p
    kernel32.SetThreadExecutionState.argtypes = [wintypes.DWORD]
    kernel32.SetThreadExecutionState.restype = wintypes.DWORD
    kernel32.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(FILETIME)] * 4
    kernel32.GetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_size_t),
                                                ctypes.POINTER(ctypes.c_size_t)]
    kernel32.SetProcessAffinityMask.argtypes = [wintypes.HANDLE, ctypes.c_size_t]
    kernel32.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR,
                                                    ctypes.POINTER(wintypes.DWORD)]
    kernel32.GetPriorityClass.argtypes = [wintypes.HANDLE]
    user32.GetForegroundWindow.restype = wintypes.HWND
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    ntdll.NtQueryInformationProcess.argtypes = [wintypes.HANDLE, ctypes.c_ulong, ctypes.c_void_p, ctypes.c_ulong,
                                                ctypes.POINTER(ctypes.c_ulong)]


class WindowsPlatform(Platform):
    name = 'windows'
    PRIORITY = {0x20: 'normal', 0x40: 'idle', 0x80: 'high', 0x100: 'realtime', 0x4000: 'below_normal',
                0x8000: 'above_normal'}

    def __init__(self):
        self._pdh = None
        self._pdh_error = None

    def _open(self, pid, access=PROCESS_QUERY_LIMITED_INFORMATION if WINDOWS else 0):
        h = kernel32.OpenProcess(access, False, pid)
        if not h:
            raise ctypes.WinError(ctypes.get_last_error())
        return h

    def processes(self):
        snap = kernel32.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)
        if snap == INVALID_HANDLE_VALUE or not snap:
            raise ctypes.WinError(ctypes.get_last_error())
        out = {}
        try:
            entry = PROCESSENTRY32W()
            entry.dwSize = ctypes.sizeof(entry)
            more = kernel32.Process32FirstW(snap, ctypes.byref(entry))
            if not more:
                raise ctypes.WinError(ctypes.get_last_error())
            while more:
                pid = entry.th32ProcessID
                info = {'pid': pid, 'ppid': entry.th32ParentProcessID, 'name': entry.szExeFile, 'cpu_s': None}
                start = 0
                if pid:
                    h = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
                    if h:
                        c, e, k, u = FILETIME(), FILETIME(), FILETIME(), FILETIME()
                        if kernel32.GetProcessTimes(h, ctypes.byref(c), ctypes.byref(e), ctypes.byref(k), ctypes.byref(u)):
                            info['cpu_s'] = k.seconds() + u.seconds()
                            start = (c.hi << 32) | c.lo
                        kernel32.CloseHandle(h)
                out[(pid, start)] = info
                more = kernel32.Process32NextW(snap, ctypes.byref(entry))
            if ctypes.get_last_error() != 18:  # ERROR_NO_MORE_FILES is the only successful end.
                raise ctypes.WinError(ctypes.get_last_error())
        finally:
            kernel32.CloseHandle(snap)
        return out

    def system_times(self):
        idle, kern, user = FILETIME(), FILETIME(), FILETIME()
        if not kernel32.GetSystemTimes(ctypes.byref(idle), ctypes.byref(kern), ctypes.byref(user)):
            raise ctypes.WinError(ctypes.get_last_error())
        # Kernel time includes idle time.
        return idle.seconds(), kern.seconds() + user.seconds()

    def command_line(self, pid):
        try:
            h = self._open(pid)
        except OSError:
            return self._command_line_cim(pid)
        try:
            size = ctypes.c_ulong(0)
            ntdll.NtQueryInformationProcess(h, 60, None, 0, ctypes.byref(size))
            if not size.value:
                return self._command_line_cim(pid)
            buf = ctypes.create_string_buffer(size.value)
            status = ntdll.NtQueryInformationProcess(h, 60, buf, size, ctypes.byref(size))
            if status != 0:
                return self._command_line_cim(pid)
            return parse_unicode_string_buffer(buf.raw, ctypes.sizeof(ctypes.c_void_p))
        finally:
            kernel32.CloseHandle(h)

    @staticmethod
    def _command_line_cim(pid):
        probe = subprocess.run(['powershell', '-NoProfile', '-Command',
                                f'(Get-CimInstance Win32_Process -Filter "ProcessId = {int(pid)}").CommandLine'],
                               capture_output=True, text=True)
        return probe.stdout.strip() or None

    def executable(self, pid):
        try:
            h = self._open(pid)
        except OSError:
            return None
        try:
            buf = ctypes.create_unicode_buffer(1024)
            size = wintypes.DWORD(1024)
            if kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                return buf.value
            return None
        finally:
            kernel32.CloseHandle(h)

    def topology(self):
        source = 'windows:GetLogicalProcessorInformationEx(RelationProcessorCore)'
        try:
            size = wintypes.DWORD(0)
            kernel32.GetLogicalProcessorInformationEx(0, None, ctypes.byref(size))
            buf = ctypes.create_string_buffer(size.value)
            if not kernel32.GetLogicalProcessorInformationEx(0, buf, ctypes.byref(size)):
                raise ctypes.WinError(ctypes.get_last_error())
            return topology_from_cores(parse_processor_core_relations(buf.raw[:size.value]), source)
        except Exception as e:  # noqa: BLE001 - reported, never hidden
            return error(e, source)

    def power_plan(self):
        source = 'windows:PowerGetActiveScheme'
        try:
            powrprof = ctypes.WinDLL('powrprof')
            pguid = ctypes.POINTER(GUID)()
            if powrprof.PowerGetActiveScheme(None, ctypes.byref(pguid)) != 0:
                return unavailable('PowerGetActiveScheme failed', source)
            guid = pguid.contents.text()
            name = None
            size = wintypes.DWORD(0)
            if powrprof.PowerReadFriendlyName(None, pguid, None, None, None, ctypes.byref(size)) == 0 and size.value:
                buf = ctypes.create_string_buffer(size.value)
                if powrprof.PowerReadFriendlyName(None, pguid, None, None, buf, ctypes.byref(size)) == 0:
                    name = buf.raw.decode('utf-16-le', 'replace').rstrip('\0')
            kernel32.LocalFree(pguid)
            return ok({'guid': guid, 'name': name or POWER_SCHEMES.get(guid)}, source)
        except Exception as e:  # noqa: BLE001
            return error(e, source)

    def foreground(self):
        source = 'windows:GetForegroundWindow (owning process name only)'
        try:
            hwnd = user32.GetForegroundWindow()
            if not hwnd:
                return ok({'pid': None, 'exe': None, 'note': 'no foreground window (locked or secure desktop)'}, source)
            pid = wintypes.DWORD(0)
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            exe = self.executable(pid.value)
            return ok({'pid': pid.value, 'exe': os.path.basename(exe) if exe else None}, source)
        except Exception as e:  # noqa: BLE001
            return error(e, source)

    def affinity(self, pid):
        source = 'windows:GetProcessAffinityMask'
        try:
            h = self._open(pid)
            try:
                proc, system = ctypes.c_size_t(0), ctypes.c_size_t(0)
                if not kernel32.GetProcessAffinityMask(h, ctypes.byref(proc), ctypes.byref(system)):
                    raise ctypes.WinError(ctypes.get_last_error())
                return ok(mask_to_processors(proc.value), source)
            finally:
                kernel32.CloseHandle(h)
        except Exception as e:  # noqa: BLE001
            return error(e, source)

    def set_affinity(self, pid, processors):
        h = self._open(pid, PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_SET_INFORMATION)
        try:
            # Applies to every thread of the process, including threads created before the call.
            if not kernel32.SetProcessAffinityMask(h, processors_to_mask(processors)):
                raise ctypes.WinError(ctypes.get_last_error())
        finally:
            kernel32.CloseHandle(h)

    def priority(self, pid):
        source = 'windows:GetPriorityClass'
        try:
            h = self._open(pid)
            try:
                value = kernel32.GetPriorityClass(h)
                return ok(self.PRIORITY.get(value, hex(value)), source) if value else unavailable('GetPriorityClass failed', source)
            finally:
                kernel32.CloseHandle(h)
        except Exception as e:  # noqa: BLE001
            return error(e, source)

    def _pdh_open(self):
        pdh = ctypes.WinDLL('pdh')
        query = ctypes.c_void_p()
        if pdh.PdhOpenQueryW(None, None, ctypes.byref(query)) != 0:
            raise OSError('PdhOpenQueryW failed')
        counters = {}
        for key, path in (('perf', r'\Processor Information(*)\% Processor Performance'),
                          ('nominal', r'\Processor Information(_Total)\Processor Frequency')):
            handle = ctypes.c_void_p()
            status = pdh.PdhAddEnglishCounterW(query, ctypes.c_wchar_p(path), None, ctypes.byref(handle))
            if status != 0:
                raise OSError('PdhAddEnglishCounterW %s failed: 0x%08x' % (path, status & 0xFFFFFFFF))
            counters[key] = handle
        pdh.PdhCollectQueryData(query)  # Rate counters need two collections.
        self._pdh = (pdh, query, counters)

    def frequency(self, topology):
        source = r'windows:PDH \Processor Information(*)\% Processor Performance x Processor Frequency'
        if self._pdh is None and self._pdh_error is None:
            try:
                self._pdh_open()
            except Exception as e:  # noqa: BLE001
                self._pdh_error = e
        if self._pdh is None:
            return error(self._pdh_error, source)
        try:
            pdh, query, counters = self._pdh
            if pdh.PdhCollectQueryData(query) != 0:
                return unavailable('PdhCollectQueryData failed', source)
            value = PDH_FMT_COUNTERVALUE()
            if pdh.PdhGetFormattedCounterValue(counters['nominal'], 0x200, None, ctypes.byref(value)) != 0:
                return unavailable('nominal frequency counter not ready', source)
            nominal = value.doubleValue
            size, count = wintypes.DWORD(0), wintypes.DWORD(0)
            pdh.PdhGetFormattedCounterArrayW(counters['perf'], 0x200, ctypes.byref(size), ctypes.byref(count), None)
            buf = (ctypes.c_byte * size.value)()
            if pdh.PdhGetFormattedCounterArrayW(counters['perf'], 0x200, ctypes.byref(size), ctypes.byref(count), buf) != 0:
                return unavailable('processor performance counter not ready', source)
            items = ctypes.cast(buf, ctypes.POINTER(PDH_FMT_COUNTERVALUE_ITEM_W))
            mhz = {}
            for i in range(count.value):
                index = pdh_instance_processor(items[i].szName)
                if index is not None and items[i].FmtValue.CStatus in (0, 1):
                    mhz[index] = nominal * items[i].FmtValue.doubleValue / 100.0
            if not mhz:
                return unavailable('no per-processor values', source)
            return ok(summarize_frequency(mhz, nominal, topology), source)
        except Exception as e:  # noqa: BLE001
            return error(e, source)

    def memory(self):
        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [('dwLength', wintypes.DWORD), ('dwMemoryLoad', wintypes.DWORD), ('ullTotalPhys', ctypes.c_uint64),
                        ('ullAvailPhys', ctypes.c_uint64), ('ullTotalPageFile', ctypes.c_uint64),
                        ('ullAvailPageFile', ctypes.c_uint64), ('ullTotalVirtual', ctypes.c_uint64),
                        ('ullAvailVirtual', ctypes.c_uint64), ('ullAvailExtendedVirtual', ctypes.c_uint64)]
        m = MEMORYSTATUSEX()
        m.dwLength = ctypes.sizeof(m)
        if not kernel32.GlobalMemoryStatusEx(ctypes.byref(m)):
            return unavailable('GlobalMemoryStatusEx failed', 'windows:GlobalMemoryStatusEx')
        return ok({'available_mb': m.ullAvailPhys / 2 ** 20, 'load_pct': m.dwMemoryLoad}, 'windows:GlobalMemoryStatusEx')

    def static_info(self):
        import platform as pf
        model = None
        try:
            import winreg
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r'HARDWARE\DESCRIPTION\System\CentralProcessor\0') as k:
                model = winreg.QueryValueEx(k, 'ProcessorNameString')[0].strip()
        except OSError:
            pass
        return {'platform': 'windows', 'os': pf.platform(), 'cpu_model': model, 'logical_processors': os.cpu_count()}

    def keep_awake(self):
        # ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED for this thread until the process exits.
        previous = kernel32.SetThreadExecutionState(0x80000000 | 0x1 | 0x2)
        return ok(bool(previous), 'windows:SetThreadExecutionState') if previous else \
            unavailable('SetThreadExecutionState failed', 'windows:SetThreadExecutionState')

    def close(self):
        if self._pdh:
            pdh, query, _ = self._pdh
            pdh.PdhCloseQuery(query)
            self._pdh = None


def current():
    return WindowsPlatform() if WINDOWS else LinuxPlatform()


# ---------------------------------------------------------------------------------------------------------------

class NvidiaSmi:
    """One long-running `nvidia-smi --query-gpu ... -lms <interval>` process; lines are timestamped on receipt."""
    FIELDS = ('utilization.gpu', 'clocks.gr', 'clocks.sm', 'clocks.mem', 'pstate', 'temperature.gpu', 'power.draw')
    REASONS = ('clocks_event_reasons.active', 'clocks_throttle_reasons.active')
    STATIC = ('name', 'driver_version', 'pci.bus_id', 'clocks.max.gr', 'clocks.max.mem', 'power.limit')

    def __init__(self, executable='nvidia-smi', interval_ms=1000):
        self.executable = executable
        self.interval_ms = interval_ms
        self.process = None
        self.fields = None
        self.samples = []
        self.status = None
        self._thread = None

    def _query(self, fields):
        return subprocess.run([self.executable, '--query-gpu=' + ','.join(fields), '--format=csv,noheader,nounits'],
                              capture_output=True, text=True, timeout=20)

    def static(self):
        try:
            r = self._query(self.STATIC)
        except (OSError, subprocess.TimeoutExpired) as e:
            self.status = unavailable(f'{self.executable} not runnable: {e}', 'nvidia-smi')
            return self.status
        if r.returncode != 0:
            self.status = unavailable(f'{self.executable} exited {r.returncode}: {r.stderr.strip()[:200]}', 'nvidia-smi')
            return self.status
        rows = [dict(zip(self.STATIC, (v.strip() for v in line.split(',')))) for line in r.stdout.strip().splitlines()]
        self.status = ok(rows, 'nvidia-smi --query-gpu')
        return self.status

    def start(self):
        import threading
        if self.status is None:
            self.static()
        if self.status['status'] != 'ok':
            return self.status
        fields = list(self.FIELDS)
        for reason in self.REASONS:
            try:
                if self._query(fields + [reason]).returncode == 0:
                    fields.append(reason)
                    break
            except (OSError, subprocess.TimeoutExpired):
                pass
        self.fields = fields
        self.process = subprocess.Popen([self.executable, '--query-gpu=' + ','.join(fields),
                                         '--format=csv,noheader,nounits', '-lms', str(self.interval_ms)],
                                        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        self._thread = threading.Thread(target=self._read, daemon=True, name='aa-nvidia-smi')
        self._thread.start()
        return self.status

    def _read(self):
        for line in self.process.stdout:
            self.samples.append(parse_nvidia_line(line, self.fields, time.time()))

    def stop(self):
        if self.process:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self._thread.join(timeout=5)


def parse_nvidia_line(line, fields, t):
    out = {'t': t}
    for k, v in zip(fields, (x.strip() for x in line.split(','))):
        if k == 'pstate' or k.endswith('reasons.active'):
            out[k] = v
            continue
        try:
            out[k] = float(v)
        except ValueError:
            out[k] = None  # "[N/A]" or "[Not Supported]": missing, not zero
    return out
