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

	# --dest-os=android exists in Node's configure; fall back to linux semantics
	# (libuv and V8 treat bionic as Linux) if a future version drops it, and say
	# which path was taken so the artifact is not ambiguous.
	if ! ./configure \
		--prefix="$KODRA_PREFIX" \
		--dest-os=android \
		--dest-cpu=arm64 \
		--cross-compiling \
		--with-intl=none \
		--openssl-no-asm \
		--shared-openssl=no \
		CC="$CC" CXX="$CXX" CC_host="$CC_host" CXX_host="$CXX_host" LINK_host="$LINK_host" \
		CFLAGS="$ANDROID_CFLAGS -O2" \
		CXXFLAGS="$ANDROID_CFLAGS -O2 -fno-rtti" \
		LDFLAGS="$ANDROID_LDFLAGS" \
		>configure-android.log 2>&1; then
		cat configure-android.log
		ci_notice "node" "--dest-os=android rejected, retrying with --dest-os=linux"
		./configure \
			--prefix="$KODRA_PREFIX" \
			--dest-os=linux \
			--dest-cpu=arm64 \
			--cross-compiling \
			--with-intl=none \
			--openssl-no-asm \
			--shared-openssl=no \
			CC="$CC" CXX="$CXX" CC_host="$CC_host" CXX_host="$CXX_host" LINK_host="$LINK_host" \
			CFLAGS="$ANDROID_CFLAGS -O2 -D__ANDROID__" \
			CXXFLAGS="$ANDROID_CFLAGS -O2 -fno-rtti -D__ANDROID__" \
			LDFLAGS="$ANDROID_LDFLAGS" || return 1
	fi

	grep -E '^(CC|CXX|dest_os|dest_cpu)' out/Makefile 2>/dev/null || true

	make -j"$(nproc)" || return 1
	make install DESTDIR="$KODRA_ROOTFS" || return 1
}
