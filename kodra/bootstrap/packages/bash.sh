# bash 5.2 — the terminal's default shell.
#
# The configure cache variables below answer autoconf's run-time checks for a
# cross build. They are not guesses: each one is what a real bionic Android
# device reports (getcwd uses malloc, job control works, named pipes exist,
# sigsetjmp is a function, and printf handles %a). Without them configure either
# refuses to run the check with a host binary or picks the host's answer.
PKG_VERSION=5.2.37
PKG_URL="https://ftp.gnu.org/gnu/bash/bash-5.2.37.tar.gz"
# GNU publishes a signature, not a checksum list. This digest is what the first
# build observed over TLS (`build.sh pin`) and is enforced from then on.
PKG_SHA256=9599b22ecd1d5787ad7d3b7bf0c59f312b3396d1e281175dd1f8a4014da621ff

build() {
	local archive
	archive="$(pkg_fetch "$PKG_URL" "$PKG_SHA256")" || return 1
	pkg_extract "$archive" "$PKG_DIR" >/dev/null || return 1
	cd "$PKG_DIR/bash-$PKG_VERSION" || return 1

	# --without-bash-malloc: bionic's allocator is the only one that cooperates with
	# the Android low-memory killer and with mspace used by the workbench.
	# --disable-nls: no message catalogues in the bootstrap.
	./configure \
		--host="$TARGET_TRIPLE" \
		--build="$(uname -m)-linux-gnu" \
		--prefix="$KODRA_PREFIX" \
		--without-bash-malloc \
		--disable-nls \
		CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" \
		# HAVE_UNISTD_H: bash's bundled termcap compiles with its own include list, and
		# without this its tparam.c calls write() with no prototype — an error in C99 and
		# later, not a warning. Android does have unistd.h.
		CFLAGS="$ANDROID_CFLAGS -O2 -DHAVE_UNISTD_H=1" \
		LDFLAGS="$ANDROID_LDFLAGS" \
		bash_cv_getcwd_malloc=yes \
		bash_cv_job_control_missing=present \
		bash_cv_sys_named_pipes=present \
		bash_cv_func_sigsetjmp=present \
		bash_cv_printf_a_format=yes \
		bash_cv_wcwidth_broken=no \
		bash_cv_unusable_rtsigs=no \
		bash_cv_must_reinstall_sighandlers=no || return 1

	make -j"$(nproc)" || return 1
	make install DESTDIR="$KODRA_ROOTFS" || return 1
}
