# git — the binary the built-in Git extension runs. Not a reimplementation: the
# extension shells out to this and parses its output, so it must be real git.
#
# NO_OPENSSL/NO_CURL for now: TLS git needs OpenSSL and libcurl in the prefix,
# which arrive in a later package of this same stage. Without them git works on
# local and file:// repositories and reports the gap explicitly instead of
# pretending https works.
PKG_VERSION=2.47.1
# kernel.org is the canonical source and publishes sha256sums.txt alongside it.
PKG_URL="https://www.kernel.org/pub/software/scm/git/git-$PKG_VERSION.tar.gz"
PKG_SHA256=PIN

build() {
	local archive
	archive="$(pkg_fetch "$PKG_URL" "$PKG_SHA256")" || return 1
	pkg_extract "$archive" "$PKG_DIR" >/dev/null || return 1
	cd "$PKG_DIR/git-$PKG_VERSION" || return 1

	make -j"$(nproc)" \
		CC="$CC" AR="$AR" RANLIB="$RANLIB" \
		CFLAGS="$ANDROID_CFLAGS -O2 -I$KODRA_PREFIX/include" \
		LDFLAGS="$ANDROID_LDFLAGS -L$KODRA_PREFIX/lib" \
		prefix="$KODRA_PREFIX" \
		gitexecdir="$KODRA_PREFIX/libexec/git-core" \
		template_dir="$KODRA_PREFIX/share/git-core/templates" \
		NO_GETTEXT=1 \
		NO_TCLTK=1 \
		NO_PERL=1 \
		NO_PYTHON=1 \
		NO_EXPAT=1 \
		NO_ICONV=1 \
		NO_OPENSSL=1 \
		NO_CURL=1 \
		NO_LIBPCRE=1 \
		NO_INSTALL_HARDLINKS=1 \
		SHELL_PATH="$KODRA_PREFIX/bin/bash" \
		INSTALL_SYMLINKS=1 \
		install || return 1
}
