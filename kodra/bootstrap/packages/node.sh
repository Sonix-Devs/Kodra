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
	# Both wrappers record their argv, tagged with which one ran: when a link fails,
	# make does not echo the command, and 'was this linked by the C or the C++ driver'
	# is the difference between an object being on the command line and not.
	#
	# The cpufeatures object and -llog belong to link lines only; on compiles they would
	# draw 'linker input unused' from every one of the 1 600 objects. Both wrappers need
	# the link branch, because gyp's LINK.target can resolve to either driver for a
	# target built through the C compiler.
	printf '#!/bin/sh\nprintf "cc %%s\\n" "$*" >>%s\ncase " $* " in\n*" -c "*) exec %s %s -std=gnu17 -O2 "$@" ;;\nesac\nexec %s %s %s "$@" -llog %s\n' \
		"$PKG_DIR/cc.log" "$CC" "$ANDROID_CFLAGS" \
		"$CC" "$ANDROID_CFLAGS" "$ANDROID_LDFLAGS" "$PKG_DIR/cpu-features.o" >"$PKG_DIR/bin/node-cc"
	# -std goes after "$@": gyp appends its own flags to the wrapper's, and node 24 needs
	# C++20, so the last word on the command line has to be C++20.
	printf '#!/bin/sh\nprintf "cxx %%s\\n" "$*" >>%s\ncase " $* " in\n*" -c "*) exec %s %s -O2 -fno-rtti "$@" -std=gnu++20 ;;\nesac\nexec %s %s -O2 -fno-rtti %s "$@" -std=gnu++20 -llog %s\n' \
		"$PKG_DIR/cc.log" "$CXX" "$ANDROID_CFLAGS" \
		"$CXX" "$ANDROID_CFLAGS" "$ANDROID_LDFLAGS" "$PKG_DIR/cpu-features.o" >"$PKG_DIR/bin/node-cxx"
	chmod +x "$PKG_DIR/bin/node-cc" "$PKG_DIR/bin/node-cxx"
	# Node's bundled zlib calls android_getCpuFeatures() whenever ARMV8_OS_ANDROID is
	# defined, and gyp adds only the NDK's cpufeatures *include* directory, never an
	# implementation: upstream expects the Android build to import the NDK's cpufeatures
	# module. It is compiled here, from the NDK's own cpu-features.c, and placed on the
	# link line — libandroid.so is not an option, since those symbols are private system
	# API and were removed from it for API 29+.
	if ! node_cpu_features; then
		return 1
	fi

	if ! node_probe; then
		return 1
	fi

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

	# pipefail is set by the driver, so `make | tee` reports make's own status — but
	# only if the failure is caught here; left bare it would trip set -e and abort the
	# recipe before the failure is reported.
	local rc=0
	make -j"$(nproc)" 2>&1 | tee "$PKG_DIR/make.log" || rc=$?
	if [ "$rc" -ne 0 ]; then
		node_report_failure "$PKG_DIR/make.log" "$rc"
		return 1
	fi
	make install DESTDIR="$KODRA_ROOTFS" || return 1
}

# node_cpu_features — compile the NDK's cpufeatures implementation for the target into
# $PKG_DIR/cpu-features.o, which the wrappers put on every link line. Two attempts,
# because a first failure says little: the package wrapper with every Android flag, then
# the toolchain wrapper alone, the combination the <memory> probe showed to work.
node_cpu_features() {
	local src="$ANDROID_NDK_ROOT/sources/android/cpufeatures/cpu-features.c"
	local out="$PKG_DIR/cpu-features.o" a=1 b=1 line
	if [ ! -f "$src" ]; then
		printf 'node: error: no sources/android/cpufeatures/cpu-features.c in the NDK\n' >&2
		return 1
	fi
	"$PKG_DIR/bin/node-cc" -c "$src" -o "$out" >"$PKG_DIR/cpu-features-a.log" 2>&1 || a=$?
	if [ "$a" -ne 0 ]; then
		"$CC" -std=gnu17 -O2 -c "$src" -o "$out" >"$PKG_DIR/cpu-features-b.log" 2>&1 || b=$?
	fi
	if [ "$a" -eq 0 ] || [ "$b" -eq 0 ]; then
		return 0
	fi
	# Both attempts failed, so report what the logs actually hold rather than grepping
	# them: an empty log with a non-zero status is itself the finding, and a grep for
	# 'error' would hide it.
	printf 'node: error: cpufeatures a=%s b=%s logs=%s+%sB src=%sB\n' "$a" "$b" \
		"$(wc -c <"$PKG_DIR/cpu-features-a.log" 2>/dev/null || echo '?')" \
		"$(wc -c <"$PKG_DIR/cpu-features-b.log" 2>/dev/null || echo '?')" \
		"$(wc -c <"$src" 2>/dev/null || echo '?')" >&2
	printf 'node: error: a said: %.150s\n' \
		"$(tail -c 200 "$PKG_DIR/cpu-features-a.log" 2>/dev/null || true)" >&2
	printf 'node: error: b said: %.150s\n' \
		"$(tail -c 200 "$PKG_DIR/cpu-features-b.log" 2>/dev/null || true)" >&2
	printf 'node: error: cc compile branch: %.150s\n' \
		"$(sed -n '3p' "$PKG_DIR/bin/node-cc" 2>/dev/null || true)" >&2
	return 1
}

# node_probe — one translation unit through the target C++ compiler, before a 4 000
# file gyp target can fail somewhere inside a libc++ header. Three variants, because
# they split the possible causes in one run: the wrapper with the package flags, the
# toolchain wrapper alone with an explicit C++20, and the toolchain wrapper alone. If
# only the first fails, the flags in this recipe are the cause; if the last ones fail
# too, the toolchain or the headers are. Diagnostics stay short and last in the log:
# the driver reports the tail of the log and the last five error lines, and long paths
# push everything else out of both windows.
node_probe() {
	local src="$PKG_DIR/probe.cc" a=0 b=0 c=0
	printf '#include <memory>\nint main() { std::unique_ptr<int> p(new int(1)); return *p; }\n' >"$src"
	"$PKG_DIR/bin/node-cxx" -c "$src" -o "$PKG_DIR/probe-a.o" >"$PKG_DIR/probe-a.log" 2>&1 || a=$?
	"$CXX" -std=gnu++20 -c "$src" -o "$PKG_DIR/probe-b.o" >"$PKG_DIR/probe-b.log" 2>&1 || b=$?
	"$CXX" -c "$src" -o "$PKG_DIR/probe-c.o" >"$PKG_DIR/probe-c.log" 2>&1 || c=$?
	printf 'node: error: <memory> probe flags=%s cxx20=%s bare=%s (0 is ok)\n' "$a" "$b" "$c" >&2
	local line
	while IFS= read -r line; do
		printf 'node: error: %.160s\n' "$line" >&2
	done < <(grep -a -m2 'error:' "$PKG_DIR/probe-a.log" | sed -e 's|.*/||' || true)
	rm -f "$src" "$PKG_DIR"/probe-?
	[ "$a" -eq 0 ]
}

# node_report_failure <log> — a compile error inside libc++ headers says nothing about
# the build, and make never echoes the command that produced it. The object make gave up
# on is in its own error line, so the argv the wrapper recorded for that object can be
# looked up, and with it the two facts that decide header behaviour: the dialect gyp
# chose, and the target the compiler was told to build for.
#
# These lines are printed to the package log rather than through ci_error(): a recipe's
# stderr is redirected into that log, so an ::error:: written here never reaches the
# runner. The driver greps the log for 'error' lines in order to build its one
# annotation, so the text says 'error:' and stays short, and it prints five lines so
# they fill that window instead of the compiler's cascades.
node_report_failure() {
	local log="$1" rc="$2" target line driver tail_of_line sym n
	# gyp's rules name the file they were building in square brackets, and the innermost
	# failure is the first such line: the outer ones are node's own Makefile recursing.
	# The last match would be '[Makefile:143: node] Error 2', which says only that make
	# gave up. That target is an object for a compile and a binary for a link, and the
	# wrapper's log has the command that produced it.
	target="$(grep -a -oE '\[[^]]*\.mk:[0-9]+: [^]]*\] Error [0-9]+' "$log" | head -n 1 \
		| sed -e 's/\] Error [0-9]*$//' -e 's/^\[[^]]*: //' || true)"
	line=""
	if [ -n "$target" ] && [ -f "$PKG_DIR/cc.log" ]; then
		line="$(grep -a -F -- "$target" "$PKG_DIR/cc.log" | tail -n 1 || true)"
	fi
	driver="$(printf '%s' "$line" | cut -d' ' -f1)"
	# The end of the command is where this recipe's own flags are: the wrapper appends
	# the cpufeatures object and -llog to link lines, so its tail says whether the object
	# was actually there.
	tail_of_line="$(printf '%s' "$line" | rev | cut -c1-200 | rev)"
	n="$(wc -l <"$PKG_DIR/cc.log" 2>/dev/null | tr -d ' ' || true)"
	sym="?"
	if [ -f "$PKG_DIR/cpu-features.o" ]; then
		sym="$(llvm-nm --defined-only "$PKG_DIR/cpu-features.o" 2>/dev/null \
			| grep -c android_getCpuFeatures || true)"
	fi
	printf 'node: error: make rc=%s, %s invocations, cpu-features.o defines %s\n' \
		"$rc" "${n:-0}" "$sym" >&2
	printf 'node: error: failed target=%s driver=%s\n' \
		"${target##*/}" "${driver:-unknown}" >&2
	printf 'node: error: line tail: %.220s\n' "${tail_of_line:-none}" >&2
	printf 'node: error: first diagnostic: %.200s\n' "$(grep -a -m1 'error:' "$log" || true)" >&2
	printf 'node: error: link branch: %.150s\n' "$(tail -n 1 "$PKG_DIR/bin/node-cxx" || true)" >&2
	return 0
}
