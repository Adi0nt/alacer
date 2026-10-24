"""Windows native thread CPU snapshots for opt-in attribution; no UI or thread state changes."""
import ctypes as c
import os
from ctypes import wintypes as w


class ThreadEntry(c.Structure):
    _fields_ = [('size', w.DWORD), ('usage', w.DWORD), ('tid', w.DWORD), ('pid', w.DWORD),
                ('priority', w.LONG), ('delta', w.LONG), ('flags', w.DWORD)]


class ModuleEntry(c.Structure):
    _fields_ = [('size', w.DWORD), ('id', w.DWORD), ('pid', w.DWORD), ('global_usage', w.DWORD),
                ('process_usage', w.DWORD), ('base', c.c_void_p), ('bytes', w.DWORD), ('handle', w.HMODULE),
                ('name', w.WCHAR * 256), ('path', w.WCHAR * 260)]


def module_at(address, modules):
    return next((name for base, size, name in modules if address and base <= address < base + size), None)


class WindowsThreadCpu:
    def __init__(self, pid):
        if os.name != 'nt':
            raise OSError('Windows thread snapshots require Windows')
        self.pid = pid
        self.k = c.WinDLL('kernel32', use_last_error=True)
        self.nt = c.WinDLL('ntdll')
        for name, result, args in (
            ('CreateToolhelp32Snapshot', w.HANDLE, [w.DWORD, w.DWORD]),
            ('CloseHandle', w.BOOL, [w.HANDLE]),
            ('Thread32First', w.BOOL, [w.HANDLE, c.POINTER(ThreadEntry)]),
            ('Thread32Next', w.BOOL, [w.HANDLE, c.POINTER(ThreadEntry)]),
            ('Module32FirstW', w.BOOL, [w.HANDLE, c.POINTER(ModuleEntry)]),
            ('Module32NextW', w.BOOL, [w.HANDLE, c.POINTER(ModuleEntry)]),
            ('OpenThread', w.HANDLE, [w.DWORD, w.BOOL, w.DWORD]),
            ('GetThreadTimes', w.BOOL, [w.HANDLE] + [c.POINTER(c.c_uint64)] * 4),
        ):
            fn = getattr(self.k, name)
            fn.restype, fn.argtypes = result, args
        self.nt.NtQueryInformationThread.argtypes = [w.HANDLE, w.ULONG, c.c_void_p, w.ULONG, c.c_void_p]
        self.nt.NtQueryInformationThread.restype = w.LONG

    def entries(self, flags, cls, first, next_name):
        handle = self.k.CreateToolhelp32Snapshot(flags, self.pid if cls is ModuleEntry else 0)
        if handle == c.c_void_p(-1).value:
            raise c.WinError(c.get_last_error())
        try:
            entry = cls()
            entry.size = c.sizeof(entry)
            ok = getattr(self.k, first)(handle, c.byref(entry))
            if not ok:
                raise c.WinError(c.get_last_error())
            while ok:
                yield entry
                ok = getattr(self.k, next_name)(handle, c.byref(entry))
            if c.get_last_error() != 18:  # ERROR_NO_MORE_FILES
                raise c.WinError(c.get_last_error())
        finally:
            self.k.CloseHandle(handle)

    def snapshot(self):
        modules = [(m.base, m.bytes, m.name) for m in self.entries(0x18, ModuleEntry, 'Module32FirstW', 'Module32NextW')]
        rows = []
        for thread in self.entries(4, ThreadEntry, 'Thread32First', 'Thread32Next'):
            if thread.pid != self.pid:
                continue
            handle = self.k.OpenThread(0x40, False, thread.tid)
            if not handle:
                rows.append({'id': thread.tid, 'error': c.get_last_error()})
                continue
            try:
                created, exited, kernel, user = (c.c_uint64() for _ in range(4))
                if not self.k.GetThreadTimes(handle, c.byref(created), c.byref(exited), c.byref(kernel), c.byref(user)):
                    rows.append({'id': thread.tid, 'error': c.get_last_error()})
                    continue
                address = c.c_void_p()
                status = self.nt.NtQueryInformationThread(handle, 9, c.byref(address), c.sizeof(address), None)
                rows.append({'id': thread.tid, 'created': created.value, 'cpu_s': (kernel.value + user.value) / 1e7,
                             'user_s': user.value / 1e7, 'kernel_s': kernel.value / 1e7,
                             'start': address.value if status == 0 else None, 'start_status': status,
                             'module': module_at(address.value, modules) if status == 0 else None})
            finally:
                self.k.CloseHandle(handle)
        return rows
