#!/usr/bin/env bash
# Provides a real Android/bionic userland and a way to execute Kodra binaries on it.
#
# The problem this solves
# -----------------------
# A GitHub runner is Linux with a glibc userland. A Kodra binary is Android: its ELF
# interpreter is /system/bin/linker64 and its libraries are bionic. Executing it
# directly on the runner fails with "No such file or directory" (the interpreter),
# and faking that with a shim would prove nothing.
#
# So this script installs the genuine article: /system/bin/linker64 and /system/lib64
# copied out of an official arm64 Android system image, inside a chroot at their real
# paths. Kodra's $PREFIX is bind-mounted at its real device path inside that chroot,
# so binaries find their own rpath and behave exactly as they will on the device.
#
# CPU is the runner's own aarch64 core; nothing is emulated. The kernel is the
# runner's Linux kernel, which is the same syscall surface bionic targets. This is
# not a substitute for a device — Play services, SELinux and OEM kernels are absent —
# but it does execute the real linker, the real libc and the real binaries.
#
# Usage:
#   android-runtime.sh install            # download image, build the chroot
#   android-runtime.sh run <cmd...>       # run a command inside the chroot
#   android-runtime.sh shell -c <script>  # run through /system/bin/sh
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/ci.sh
. "$here/lib/ci.sh"

ANDROID_API="${ANDROID_API:-28}"
BUILD_ROOT="${BUILD_ROOT:-$PWD/build-out}"
ROOT="${ANDROID_ROOT:-$BUILD_ROOT/android-root}"
ROOTFS="${KODRA_ROOTFS:-$BUILD_ROOT/rootfs}"
KODRA_PREFIX="${KODRA_PREFIX:-/data/data/dev.sonixdevs.kodra/files/usr}"
# The whole app-private files/ tree is mounted (not just usr/), because $HOME lives
# beside the prefix and git, npm and ssh all write into it.
FILES_DIR="$(dirname "$KODRA_PREFIX")"
KODRA_HOME="$FILES_DIR/home"
# Candidates are tried in order; arm64 images are not published for every API/flavour
# combination, and the first one that installs is the one used.
IMAGE_CANDIDATES="${IMAGE_CANDIDATES:-system-images;android-${ANDROID_API};default;arm64-v8a system-images;android-${ANDROID_API};google_apis;arm64-v8a system-images;android-30;google_apis;arm64-v8a system-images;android-31;google_apis;arm64-v8a}"

sdkmanager_bin() {
	if command -v sdkmanager >/dev/null 2>&1; then
		command -v sdkmanager
	elif [ -n "${ANDROID_HOME:-}" ] && [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
		printf '%s' "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
	else
		return 1
	fi
}

# Android system images may ship system.img in Android's sparse format, which cannot be
# mounted or read by debugfs. lib/sparse2raw.py converts it in place and is a no-op when
# the image is already raw.
desparse() {
	python3 "$here/lib/sparse2raw.py" "$1"
}

install() {
	local smgr image found=""
	smgr="$(sdkmanager_bin)" || {
		ci_error "android-runtime" "sdkmanager not found; android-actions/setup-android must run first"
		return 1
	}
	yes | "$smgr" --licenses >/dev/null 2>&1 || true
	for image in $IMAGE_CANDIDATES; do
		if "$smgr" --install "$image" >"$BUILD_ROOT/sdkmanager.log" 2>&1; then
			found="$image"
			break
		fi
	done
	[ -n "$found" ] || {
		ci_annotate_log "android-runtime" "$BUILD_ROOT/sdkmanager.log" 'Warning|Error|error'
		return 1
	}
	ci_notice "android-runtime" "system image: $found"

	local image_dir img
	image_dir="${ANDROID_HOME:-/usr/local/lib/android/sdk}/system-images/$(printf '%s' "$found" | cut -d';' -f2-5 | tr ';' '/')"
	if [ ! -d "$image_dir" ]; then
		image_dir="$(dirname "$(find "${ANDROID_HOME:-/usr/local/lib/android/sdk}/system-images" -name system.img -print -quit)")"
	fi
	img="$image_dir/system.img"
	[ -f "$img" ] || {
		ci_error "android-runtime" "system.img not found under $image_dir"
		return 1
	}
	desparse "$img"

	# The chroot is a directory tree whose /system is the image itself.
	sudo mkdir -p "$ROOT/system" "$ROOT/proc" "$ROOT/dev/pts" "$ROOT/tmp"
	sudo mount -o loop,ro "$img" "$ROOT/system" || {
		ci_error "android-runtime" "could not loop-mount $img (loop devices unavailable?)"
		return 1
	}
	[ -x "$ROOT/system/bin/linker64" ] || {
		ci_error "android-runtime" "$found has no /system/bin/linker64; wrong image architecture"
		return 1
	}

	# Device paths inside the chroot: files/ keeps its real path so rpath and
	# baked-in configuration resolve without wrappers.
	mkdir -p "$ROOTFS$KODRA_PREFIX" "$ROOTFS$KODRA_HOME" "$ROOTFS$KODRA_PREFIX/tmp"
	sudo mkdir -p "$ROOT$FILES_DIR"
	sudo mount --bind "$ROOTFS$FILES_DIR" "$ROOT$FILES_DIR"
	sudo mount --bind /proc "$ROOT/proc"
	sudo mount --bind /dev "$ROOT/dev" || ci_notice "android-runtime" "/dev bind failed; PTY tests will need devpts only"
	sudo mount -t devpts -o newinstance,mode=620 devpts "$ROOT/dev/pts" || ci_notice "android-runtime" "devpts unavailable; node-pty test will be skipped"

	# /system/bin/sh exists in the image; prove the chroot is alive and report the
	# exact bionic build in use so the evidence is concrete.
	local probe
	probe="$(sudo chroot "$ROOT" /system/bin/linker64 --version 2>&1 | head -2 || true)"
	ci_notice "android-runtime" "linker: ${probe//$'\n'/ }"
}

run() {
	mkdir -p "$ROOTFS$KODRA_PREFIX" "$ROOTFS$KODRA_HOME" "$ROOTFS$KODRA_PREFIX/tmp"
	# Environment mirrors the app: the service sets HOME/PREFIX/TMPDIR/LD_LIBRARY_PATH
	# before spawning the server (see ServerProcess.kt).
	sudo env -i \
		HOME="$KODRA_HOME" \
		PREFIX="$KODRA_PREFIX" \
		TMPDIR="$KODRA_PREFIX/tmp" \
		TERM="${TERM:-xterm-256color}" \
		PATH="$KODRA_PREFIX/bin:/system/bin" \
		LD_LIBRARY_PATH="$KODRA_PREFIX/lib" \
		NODE_PATH="$KODRA_PREFIX/lib/node_modules" \
		"$(command -v chroot)" "$ROOT" "$@"
}

case "${1:-}" in
install) install ;;
run)
	shift
	run "$@"
	;;
shell)
	shift
	run /system/bin/sh "$@"
	;;
*)
	echo "usage: $0 {install|run <cmd...>|shell -c <script>}" >&2
	exit 2
	;;
esac
