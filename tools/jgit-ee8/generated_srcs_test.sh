#!/usr/bin/env bash
# Copyright (C) 2026, David Ostrovsky <david@ostrovsky.org> and others
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Distribution License v. 1.0 which is available at
# http://www.eclipse.org/org/documents/edl-v10.php.
#
# SPDX-License-Identifier: BSD-3-Clause

set -euo pipefail

check_module() {
  local src_prefix="$1"
  local srcjar="$2"
  shift 2

  local tmp
  tmp="$(mktemp -d "${TEST_TMPDIR:-/tmp}/jgit-ee8-srcs.XXXXXX")"

  local expected="${tmp}/expected"
  local actual="${tmp}/actual"
  : > "${expected}"

  local source
  for source in "$@"; do
    local entry="${source#*"${src_prefix}"}"
    if [[ "${entry}" == "${source}" ]]; then
      echo "Could not strip ${src_prefix} from ${source}" >&2
      exit 1
    fi
    echo "${entry}" >> "${expected}"

    local source_lines generated_lines
    source_lines="$(wc -l < "${source}" | tr -d ' ')"
    generated_lines="$(unzip -p "${srcjar}" "${entry}" | wc -l | tr -d ' ')"
    if [[ "${source_lines}" != "${generated_lines}" ]]; then
      echo "Line count changed for ${entry}: ${source_lines} -> ${generated_lines}" >&2
      exit 1
    fi
  done

  zipinfo -1 "${srcjar}" | sort > "${actual}"
  sort "${expected}" -o "${expected}"
  if ! diff -u "${expected}" "${actual}"; then
    echo "Generated source jar entries do not match input source list" >&2
    exit 1
  fi

  local all_sources="${tmp}/all-sources"
  local all_original_sources="${tmp}/all-original-sources"
  unzip -p "${srcjar}" > "${all_sources}"
  cat "$@" > "${all_original_sources}"

  if grep -n 'jakarta\.servlet' "${all_sources}"; then
    echo "Found jakarta.servlet residue in ${srcjar}" >&2
    exit 1
  fi

  if grep -n '^import jakarta\.' "${all_sources}"; then
    echo "Found jakarta import residue in ${srcjar}" >&2
    exit 1
  fi

  if grep -n 'org\.eclipse\.jetty\.ee10\.servlet' "${all_sources}"; then
    echo "Found Jetty EE10 servlet residue in ${srcjar}" >&2
    exit 1
  fi

  if grep -n 'org\.eclipse\.jgit\..*\.ee8' "${all_sources}"; then
    echo "Found package-renamed .ee8 residue in ${srcjar}" >&2
    exit 1
  fi

  if grep -q 'jakarta\.servlet' "${all_original_sources}" &&
      ! grep -q 'javax\.servlet' "${all_sources}"; then
    echo "No javax.servlet references found in ${srcjar}" >&2
    exit 1
  fi

  rm -rf "${tmp}"
}

modules_seen=0

while [[ "$#" -gt 0 ]]; do
  if [[ "$1" != "--module" ]]; then
    echo "Expected --module, got $1" >&2
    exit 1
  fi
  modules_seen=$((modules_seen + 1))
  shift
  src_prefix="$1"
  srcjar="$2"
  shift 2
  sources=()
  while [[ "$#" -gt 0 && "$1" != "--module" ]]; do
    sources+=("$1")
    shift
  done
  if [[ "${#sources[@]}" -eq 0 ]]; then
    echo "No sources passed for ${src_prefix}" >&2
    exit 1
  fi
  check_module "${src_prefix}" "${srcjar}" "${sources[@]}"
done

if [[ "${modules_seen}" -eq 0 ]]; then
  echo "No --module checks were executed" >&2
  exit 1
fi
