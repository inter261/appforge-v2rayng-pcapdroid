#!/usr/bin/env bash
# Fetches the third-party sources that are not stored in this repository:
#   - the 6 PCAPdroid submodules                       -> V2rayNG/submodules/
#   - hev-socks5-tunnel (tun2socks used by V2Ray)       -> hev-socks5-tunnel/
# Every dependency is pinned to the exact commit used by the upstream release this project is based on.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUB="$ROOT/V2rayNG/submodules"

pin() { # <dir> <url> <sha> [recursive]
  local dir="$1" url="$2" sha="$3" rec="${4:-}"
  if [ -f "$dir/.pinned-$sha" ]; then echo "up to date: ${dir#$ROOT/}"; return; fi
  echo "fetching ${dir#$ROOT/} @ ${sha:0:9}"
  rm -rf "$dir"; mkdir -p "$dir"
  git -C "$dir" init -q
  git -C "$dir" remote add origin "$url"
  git -C "$dir" fetch -q --depth 1 origin "$sha"
  git -C "$dir" checkout -q FETCH_HEAD
  if [ -n "$rec" ]; then git -C "$dir" submodule update --init --recursive --depth 1 -q; fi
  touch "$dir/.pinned-$sha"
}

# PCAPdroid 2.0.2+ (commit 12e892dd of https://github.com/emanuele-f/PCAPdroid)
pin "$SUB/MaxMind-DB-Reader-java" https://github.com/emanuele-f/MaxMind-DB-Reader-java 1f96affe0f6c37021526ebfd8993298a0f67370f
pin "$SUB/PCAPdroid-ushark-bin"   https://github.com/emanuele-f/PCAPdroid-ushark-bin   b9b0582fb05afddb88f00e03d08e95126226b66d
pin "$SUB/libpcap"                https://github.com/the-tcpdump-group/libpcap          a999701dca5c873779281938baee6bc185a8d4dc
pin "$SUB/zdtun"                  https://github.com/emanuele-f/zdtun                  c2a2674a411efdd71f7595e5395d8b49c52ab347
# Alternative SOCKS5 proxy per connection (upstream of the mitm addon through Xray)
if [ ! -f "$SUB/zdtun/.patched-alt-proxy" ]; then
  git -C "$SUB/zdtun" apply "$ROOT/scripts/zdtun-alt-proxy.patch"
  touch "$SUB/zdtun/.patched-alt-proxy"
fi
pin "$SUB/zstd"                   https://github.com/facebook/zstd                      f8745da6ff1ad1e7bab384bd1f9d742439278e99
pin "$SUB/nDPI"                   https://github.com/emanuele-f/nDPI                    40a1605bf7643488a3ac55ac2e2564dc8eaa6869

# v2rayNG 2.3.11
pin "$ROOT/hev-socks5-tunnel"     https://github.com/heiher/hev-socks5-tunnel          d9dca26c7ad0e494492244f0309e80ee583e739e recursive

# Xray core bindings (libv2ray.aar), release matching the AndroidLibXrayLite commit used by v2rayNG 2.3.11
LIBV2RAY_TAG="v26.10.10"
mkdir -p "$ROOT/V2rayNG/app/libs"
if [ ! -f "$ROOT/V2rayNG/app/libs/libv2ray.aar" ]; then
  echo "downloading libv2ray.aar $LIBV2RAY_TAG"
  curl -fL --retry 3 -o "$ROOT/V2rayNG/app/libs/libv2ray.aar" \
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/$LIBV2RAY_TAG/libv2ray.aar"
fi
echo "done"
