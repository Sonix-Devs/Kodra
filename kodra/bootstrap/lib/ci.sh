#!/usr/bin/env bash
# Helpers that turn build output into GitHub annotations.
#
# Actions job logs are stored in Azure blob storage, which the Kodra development
# sandbox cannot reach (`gh api .../jobs/<id>/logs` returns EOF). Anything a
# maintainer has to see from that environment must therefore be an annotation or
# a job summary, so every build step in this directory funnels its diagnostics
# through here.
set -uo pipefail

# ci_error <title> <message...> — single-line annotation, escaped per GitHub's rules.
ci_error() {
	local title="$1"
	shift
	local message="$*"
	message="${message//'%'/'%25'}"
	message="${message//$'\r'/'%0D'}"
	message="${message//$'\n'/'%0A'}"
	printf '::error title=%s::%s\n' "$title" "$message"
}

# ci_trap <title> — report the exact failing command of an unguarded step.
ci_trap() {
	local title="${1:-step}"
	trap 'ci_error "'"$title"'" "failed command: $BASH_COMMAND"' ERR
}

ci_notice() {
	local title="$1"
	shift
	local message="$*"
	message="${message//'%'/'%25'}"
	message="${message//$'\r'/'%0D'}"
	message="${message//$'\n'/'%0A'}"
	printf '::notice title=%s::%s\n' "$title" "$message"
}

# ci_annotate_tail <title> <file> [lines] — the last lines of a log as annotations.
#
# Pattern matching misses a lot in practice: 'make' reports a missing header, curl
# reports TLS failure, and a recipe can exit with nothing at all. When that is the
# only copy of the output a maintainer will ever see, the tail must always be sent.
ci_annotate_tail() {
	local title="$1"
	local file="$2"
	local lines="${3:-8}"
	[ -s "$file" ] || return 0
	while IFS= read -r line; do
		[ -n "$line" ] && ci_error "$title" "$line"
	done < <(tail -n "$lines" "$file")
	return 0
}

# ci_annotate_log <title> <file> [pattern] — first N matching lines become annotations.
ci_annotate_log() {
	local title="$1"
	local file="$2"
	local pattern="${3:-error:|Error|error :|fatal error|undefined reference|No such file|cannot find|FAILED|not found}"
	local max="${CI_ANNOTATE_MAX:-25}"
	[ -s "$file" ] || return 0
	local n=0
	while IFS= read -r line; do
		[ -n "$line" ] || continue
		ci_error "$title" "$line"
		n=$((n + 1))
		[ "$n" -ge "$max" ] && break
	done < <(grep -E "$pattern" "$file" | head -n "$max")
	return 0
}

# ci_summary <heading> <file> [max lines] — tail of a log into the job summary.
ci_summary() {
	local heading="$1"
	local file="$2"
	local lines="${3:-60}"
	[ -s "$file" ] || return 0
	{
		printf '### %s\n```text\n' "$heading"
		tail -n "$lines" "$file"
		printf '```\n'
	} >>"${GITHUB_STEP_SUMMARY:-/dev/null}"
}

# ci_run <title> <log> -- <command...> — run, tee to a log, annotate on failure.
ci_run() {
	local title="$1"
	local log="$2"
	shift 2
	[ "$1" = "--" ] && shift
	local rc=0
	"$@" >"$log" 2>&1 || rc=$?
	[ "$rc" -eq 0 ] && return 0
	local matched
	matched="$(grep -cE 'error:|Error|fatal|FAILED|not found|cannot|No such file' "$log" 2>/dev/null || true)"
	ci_annotate_log "$title" "$log"
	# A failure whose text matches no pattern would otherwise be invisible from an
	# environment without log access, so the tail is always emitted.
	if [ "${matched:-0}" -eq 0 ]; then
		while IFS= read -r line; do
			[ -n "$line" ] && ci_error "$title" "$line"
		done < <(tail -n 8 "$log")
	fi
	ci_summary "$title (failed, exit $rc)" "$log" 80
	return "$rc"
}
