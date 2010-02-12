#!/usr/bin/perl
# ------------------------------------------------------------
# This script fixes the license headers of all Java sources
# to use the Eclipse EDL license template and updates the
# copyright statements using author information from git blame
#
# To fix this in all revisions rewrite the history
# git filter-branch --tree-filter 'fixHeaders.pl' HEAD
# ------------------------------------------------------------
use strict;

# Table of author names, start date, end date, actual copyright owner.
#
my @author_employers = (
	[ qr/spearce\@spearce.org/, 2008, 8, 9999, 12, 'Google Inc.'],
	[ qr/\@sap.com/, 0, 0, 9999, 12, 'SAP'],
);

open( F, '-|', 'git ls-files' );
while (<F>) {
	chop;
	if (m,^org.eclipse.jgit(?:.*)?/.*\.java$,) {
		update_java_file($_);
	}
}
close F;

sub update_java_file
{
	my $old_file = shift;

	my $new_file = "$old_file.license.$$";
	my $header   = '';
	my $preamble = '';

	# header is everything before package statement
	open( I, '<', $old_file );
	while (<I>) {
		if (/^package /) {
			$preamble = $_;
			last;
		}

		$header .= $_;
	}
	die "$old_file has no package line.\n" unless $preamble;

	# preamble is everything with blanks or imports
	while (<I>) {
		$preamble .= $_;
		last unless (/^import / || /^$/);
	}
	my $lineno = $. - 1;

	my %all_years;
	my %author_years;
	my %minyear;
	my %maxyear;
	my $author_name;
	my $author_email;

	# find explicit copyright statements in sources
	my @lines = split( /\n/, $header );
	foreach my $line ( @lines ) {
		# * Copyright (c) 2008, Example Company Inc.
		# * Copyright (c) 2008, Joe Developer <joe.dev@example.org>
		# * Copyright (c) 2008, 2009 Joe Developer <joe.dev@example.org>
		# * Copyright (c) 2005-2009 Joe Developer <joe.dev@example.org>
		# * Copyright (c) 2008, 2009 Other Examples Inc.
		# * Copyright (c) 2008-2010 Example Company Inc.
		# * Copyright (C) 2009-2010, Yet More Examples Ltd.
		if( $line =~ m/Copyright \(c\) (\d{4})(?:\s*[,-]\s*(\d{4}))?,?\s*([^<>]+)\s*(<.*?>)?/i ) {
			my ($y, $y2, $n, $e) = ($1, $2, $3, $4);
			my $year = trim($y);
			$author_name = trim($n);
			$author_email = trim($e);
			my $who = $author_name;
			$who .= " $author_email" if $author_email;
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year);
			if (my $year2 = $y2) {
				update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year2);
			}
		}
	}

	# add implicit copyright statements from authors found in git blame
	open( B, '-|', 'git', 'blame', "-L$lineno,", '-C', '-w', '-p', $old_file );
	while (<B>) {
		chop;
		if (/^author (.*)$/) {
			$author_name = trim($1);
			next;
		}
		if (/^author-mail (<.*>)$/) {
			$author_email = trim($1);
			next;
		}
		if (/^author-time (\d+)$/) {
			# skip uncommitted changes
			if ($author_email eq "not.committed.yet") {
				next;
			}
			my @tm = localtime($1);
			my $year = $tm[5] + 1900;
			my $mon = $tm[4] + 1;
			$all_years{$year} = 1;
			my $who = "$author_name $author_email";
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year, $mon);
		}
	}
	close B;

	# rewrite file
	open( O, '>', $new_file );
	print O <<'EOF';
/*
EOF

	my %used_author;
	foreach my $year ( sort { $a cmp $b } keys %all_years ) {
		foreach my $who ( sort keys %author_years ) {
			next if $used_author{$who}++;
			my $copyright = format_copyright($minyear{$who}, $maxyear{$who}, $who);
			print O $copyright;
		}
	}

	print O <<'EOF';
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

EOF
	print O $preamble;
	print O while <I>;
	close I;
	close O;

	rename( $new_file, $old_file );
}

sub trim($)
{
	my $string = shift;
	$string =~ s/^\s+//;
	$string =~ s/\s+$//;
	return $string;
}

sub update_author_info
{
	my ($minyear_ref, $maxyear_ref, $all_years_ref, $author_years_ref, $who, $year, $mon) = @_;

	$who = translate_author($who, $year, $mon);
	$all_years_ref->{$year} = 1;
	$author_years_ref->{$who}{$year} = 1;

	my $y = $minyear_ref->{$who};
	if ($y < 1900) {
		$y = 9999;
	}
	if ($year < $y) {
		$minyear_ref->{$who} = $year;
	}
	$y = $maxyear_ref->{$who};
	if ($year > $y) {
		$maxyear_ref->{$who} = $year;
	}
}

sub date_cmp
{
	my ($a_year, $a_mon, $b_year, $b_mon) = @_;

	if ($a_year < $b_year) {
		return -1;
	} elsif ($a_year == $b_year) {
		return $a_mon <=> $b_mon;
	} else {
		return 1;
	}
}

sub translate_author
{
	my ($who, $year, $mon) = @_;

	return $who if not defined $mon;

	foreach my $spec (@author_employers) {
		next unless $who =~ $spec->[0];
		next if date_cmp($year, $mon, $spec->[1], $spec->[2]) < 0;
		next if date_cmp($year, $mon, $spec->[3], $spec->[4]) > 0;
		return $spec->[5];
	}
	return $who;
}

sub format_copyright {
	my ($minyear, $maxyear, $who) = @_;
	if ($minyear < $maxyear) {
		return " * Copyright (C) $minyear-$maxyear, $who\n";
	} else {
		return " * Copyright (C) $minyear, $who\n";
	}
}

