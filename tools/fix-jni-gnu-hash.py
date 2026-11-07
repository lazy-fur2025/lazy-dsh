#!/usr/bin/env python3
"""Repair the GNU hash table of a JNI library whose symbol was renamed by hand.

Why: JNI lookup goes through dlsym(), which uses `.gnu.hash`. Overwriting a name in `.dynstr`
(and its `st_name`) makes `readelf --dyn-syms` show the new name, but the bloom filter and chain
still encode the *old* hash, so dlsym() reports "No implementation found" at runtime. Rebuild the
library from source when possible (`native/build.sh engine`); this script exists for the case where
no NDK is available and the rename is the only change.

    python tools/fix-jni-gnu-hash.py path/to/libfoo.so          # report only
    python tools/fix-jni-gnu-hash.py path/to/libfoo.so --fix    # rewrite bloom + chains
"""
import struct
import sys


def gnu_hash(name: bytes) -> int:
    h = 5381
    for c in name:
        h = (h * 33 + c) & 0xFFFFFFFF
    return h


def load(path):
    data = bytearray(open(path, "rb").read())
    if data[:4] != b"\x7fELF" or data[4] != 2:
        raise SystemExit("not an ELF64 file")
    shoff, = struct.unpack_from("<Q", data, 0x28)
    shentsize, shnum, shstrndx = struct.unpack_from("<HHH", data, 0x3A)
    secs = []
    for i in range(shnum):
        off = shoff + i * shentsize
        secs.append(struct.unpack_from("<IIQQQQIIQQ", data, off))
    strtab = secs[shstrndx][4]

    def name_of(s):
        start = strtab + s[0]
        return data[start:data.index(b"\0", start)].decode()

    return data, {name_of(s): s for s in secs}


def main():
    if len(sys.argv) not in (2, 3):
        raise SystemExit(__doc__)
    path = sys.argv[1]
    apply = len(sys.argv) == 3 and sys.argv[2] == "--fix"

    data, secs = load(path)
    gh_off = secs[".gnu.hash"][4]
    ds_off, ds_size = secs[".dynsym"][4], secs[".dynsym"][5]
    str_off = secs[".dynstr"][4]

    nbuckets, symoffset, bloom_size, bloom_shift = struct.unpack_from("<IIII", data, gh_off)
    names = {}
    for i in range(ds_size // 24):
        st_name, = struct.unpack_from("<I", data, ds_off + i * 24)
        if i >= symoffset:
            start = str_off + st_name
            names[i] = data[start:data.index(b"\0", start)]

    bloom_off = gh_off + 16
    buckets_off = bloom_off + bloom_size * 8
    chains_off = buckets_off + nbuckets * 4
    on_disk = struct.unpack_from(f"<{bloom_size}Q", data, bloom_off)
    bloom = [0] * bloom_size
    buckets = [0] * nbuckets
    chains = [0] * len(names)
    last = {}

    for i in sorted(names):
        h = gnu_hash(names[i])
        bloom[(h // 64) % bloom_size] |= (1 << (h % 64)) | (1 << ((h >> bloom_shift) % 64))
        b = h % nbuckets
        if b in last:
            last[b] = i
        else:
            buckets[b] = i
            last[b] = i
        chains[i - symoffset] = h
    for b, i in last.items():
        chains[i - symoffset] |= 1

    print(f"{path}: {len(names)} exported symbols, {nbuckets} bucket(s)")
    for i, n in names.items():
        h = gnu_hash(n)
        word = on_disk[(h // 64) % bloom_size]
        hit = (word >> (h % 64)) & 1 and (word >> ((h >> bloom_shift) % 64)) & 1
        print(f"  {i}: {n.decode()}  hash=0x{h:08x}  {'ok' if hit else 'BLOOM MISS — needs --fix'}")

    if apply:
        struct.pack_into(f"<{bloom_size}Q", data, bloom_off, *bloom)
        struct.pack_into(f"<{nbuckets}I", data, buckets_off, *buckets)
        struct.pack_into(f"<{len(names)}I", data, chains_off, *chains)
        open(path, "wb").write(data)
        print("rewrote .gnu.hash")


main()
