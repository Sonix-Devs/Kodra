# Node 24.22.1 — the exact version remote/package.json pins for the REH server.
#
# Built from upstream source against API 28 rather than taken from
# nodejs.org/dist: that tarball is a glibc build whose interpreter is
# ld-linux-aarch64.so.1, which Android cannot load.
#
# --with-intl=none keeps ICU out: full ICU is a 30 MB data blob plus a host
# toolchain for icupkg, and it is a separate decision. Node reports
# process.config.variables.icu_small=false, so the gap is visible rather than
# silently patched over.
PKG_VERSION=24.22.1
PKG_URL="https://nodejs.org/dist/v24.22.1/node-v24.22.1.tar.xz"
# From https://nodejs.org/dist/v24.22.1/SHASUMS256.txt (published checksum list).
PKG_SHA256=4f5379796280cdaeb0c2f358fd04b6a16cc4fb7dfa83b5a8c91a9d963b435231

# What this package must leave in the prefix; build.sh fails the build if any of it
# is missing, so a wrong DESTDIR cannot pass as success.
PKG_ARTIFACTS="bin/node bin/npm"

build() {
	local archive src
	archive="$(pkg_fetch "$PKG_URL" "$PKG_SHA256")" || return 1
	pkg_extract "$archive" "$PKG_DIR" >/dev/null || return 1
	src="$PKG_DIR/node-v$PKG_VERSION"
	cd "$src" || return 1

	# Cross-compiling V8 needs host tools (mksnapshot, torque) built for the host.
	# On an aarch64 runner the host and the Android target share the CPU, so these
	# are ordinary native builds; they are named explicitly because Node otherwise
	# reuses CC/CXX and would try to run Android binaries on the runner.
	export CC_host="${CC_host:-clang}"
	export CXX_host="${CXX_host:-clang++}"
	export LINK_host="${LINK_host:-clang++}"
	export AR_host="${AR_host:-llvm-ar}"
	# gyp wants these spelled the way its conditions compare them.
	NODE_HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
	NODE_HOST_ARCH="$(uname -m | sed -e 's/^aarch64$/arm64/')"

	# Node's configure reads CC, CXX, CFLAGS, LDFLAGS and the CC_host family from the
	# *environment*. Passing them as `VAR=value` arguments makes gyp read them as files
	# to load ("gyp: CC_host=clang not found ... while trying to load CC_host=clang"),
	# which is exactly how this failed twice.
	#
	# A cross build needs host tools (mksnapshot, torque) built for the host: on an
	# aarch64 runner those are ordinary native builds, named explicitly so Node does not
	# reuse the Android CC and produce binaries the runner cannot execute.
	# The Android flags live in wrappers, not in the environment: node builds host tools
	# (mksnapshot, torque) in the same tree, and an exported CFLAGS/LDFLAGS is inherited
	# by those too — which linked a host binary with our Android --resource-dir and
	# failed on -lgcc, and would have produced a host tool the runner cannot execute.
	mkdir -p "$PKG_DIR/bin"
	printf '#!/bin/sh\nexec %s %s -O2 "$@"\n' "$CC" "$ANDROID_CFLAGS" >"$PKG_DIR/bin/node-cc"
	printf '#!/bin/sh\nexec %s %s -O2 -fno-rtti %s "$@"\n' \
		"$CXX" "$ANDROID_CFLAGS" "$ANDROID_LDFLAGS" >"$PKG_DIR/bin/node-cxx"
	chmod +x "$PKG_DIR/bin/node-cc" "$PKG_DIR/bin/node-cxx"

	node_configure() {
		local log="$1"
		# Positional arguments are forwarded to gyp by configure ("pass the leftover
		# non-whitespace positional arguments to GYP"), which is the only supported way to
		# define a gyp variable that configure does not expose. Three are needed:
		#   android_ndk_path — common.gypi dereferences it whenever OS=="android"
		#     (one -I into the NDK's cpufeatures headers);
		#   host_os, host_arch — v8.gyp branches on them for the host toolset, and nothing
		#     in Node or gyp defines them for this build.
		CC="$PKG_DIR/bin/node-cc" CXX="$PKG_DIR/bin/node-cxx" AR="$AR" \
			CC_host="$CC_host" CXX_host="$CXX_host" LINK_host="$LINK_host" AR_host="$AR_host" \
			./configure \
			--prefix="$KODRA_PREFIX" \
			--dest-os=android \
			--dest-cpu=arm64 \
			--cross-compiling \
			--with-intl=none \
			--openssl-no-asm \
			"-Dandroid_ndk_path=$ANDROID_NDK_ROOT" \
			"-Dhost_os=$NODE_HOST_OS" \
			"-Dhost_arch=$NODE_HOST_ARCH" >"$log" 2>&1
	}

	# --dest-os=android is not cosmetic: it sets gyp's OS variable to "android", which is
	# what makes Node's own deps pick their bionic configurations — c-ares, for one, only
	# undefines HAVE_GETSERVBYPORT_R in its config/android header, and bionic has no
	# getservbyport_r. Falling back to --dest-os=linux compiles a libc-agnostic tree and
	# fails there, so this build does not fall back: an android configure that fails is a
	# failure worth reading.
	if ! node_configure configure-android.log; then
		ci_error "node" "configure failed for --dest-os=android"
		grep -aE '(gyp|GYP|Error|error|Traceback|Exception)' configure-android.log | head -n 10 >&2 \
			|| tail -n 10 configure-android.log >&2
		return 1
	fi

	grep -E '^(CC|CXX|dest_os|dest_cpu)' out/Makefile 2>/dev/null || true

	make -j"$(nproc)" || return 1
	make install DESTDIR="$KODRA_ROOTFS" || return 1
}
