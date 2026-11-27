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

ci_notice() {
	local title="$1"
	shift
	local message="$*"
	message="${message//'%'/'%25'}"
	message="${message//$'\r'/'%0D'}"
	message="${message//$'\n'/'%0A'}"
	printf '::notice title=%s::%s\n' "$title" "$message"
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
	ci_annotate_log "$title" "$log"
	ci_summary "$title (failed, exit $rc)" "$log" 80
	return "$rc"
}
