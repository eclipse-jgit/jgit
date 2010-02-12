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

	[ qr/\@(.*\.|)google.com/, 0, 0, 9999, 12, 'Google Inc.'],
);

# License text itself.
#
my $license_text = <<'EOF';
 and other copyright owners as documented in the project's IP log.

 This program and the accompanying materials are made available
 under the terms of the Eclipse Distribution License v1.0 which
 accompanies this distribution, is reproduced below, and is
 available at http://www.eclipse.org/org/documents/edl-v10.php

 All rights reserved.

 Redistribution and use in source and binary forms, with or
 without modification, are permitted provided that the following
 conditions are met:

 - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

 - Neither the name of the Eclipse Foundation, Inc. nor the
   names of its contributors may be used to endorse or promote
   products derived from this software without specific prior
   written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
EOF

my @files = @ARGV;
unless (@files) {
	open( F, '-|', 'git ls-files' );
	@files = <F>;
	chop @files;
	close F;
}

foreach (@files) {
	if (/\.java$/ || $_ eq 'LICENSE') {
		next if $_ eq 'org.eclipse.jgit/src/org/eclipse/jgit/util/Base64.java';
		update_file(\&java_file, $_);

	} elsif (/pom\.xml$/) {
		update_file(\&pom_file, $_);

	} elsif (/\.sh$/) {
		update_file(\&sh_file, $_);
	}
}

sub java_file
{
	my $fd = shift;
	my $header = '';
	my $preamble = '';

	# header is everything before package statement
	while (<$fd>) {
		if (/^package /) {
			$preamble = $_;
			last;
		}
		$header .= $_;
	}

	# preamble is everything with blanks or imports
	while (<$fd>) {
		$preamble .= $_;
		last unless (/^import / || /^$/);
	}
	my $lineno = $. - 1;

	return ($header, $preamble, $lineno,
		"/*\n", sub { s/^/ */mg }, " */\n");
}

sub pom_file
{
	my $fd = shift;
	my $header = '';
	my $preamble = '';

	# header is everything before project
	while (<$fd>) {
		if (/<project/) {
			$preamble = $_;
			last;
		}
		$header .= $_;
	}
	my $lineno = $. - 1;

	return ($header, $preamble, $lineno,
		qq{<?xml version="1.0" encoding="UTF-8"?>\n<!--\n},
		sub { s/^(.)/  $1/mg },
		qq{-->\n});
}

sub sh_file
{
	my $fd = shift;
	my $top = <$fd>;
	my $header = '';
	my $preamble = '';

	while (<$fd>) {
		if (/^#/) {
			$header .= $_;
			next;
		}
		$preamble = $_;
		last;
	}
	my $lineno = $. - 1;

	return ($header, $preamble, $lineno, $top, sub { s/^/#/mg }, "");
}

sub update_file
{
	my $func = shift;
	my $old_file = shift;
	my $new_file = "$old_file.license.$$";

	open(I, '<', $old_file);
	my ($header, $preamble, $lineno,
		$top, $fmt, $btm) = &{$func}(\*I);

	my %all_years;
	my %author_years;
	my %minyear;
	my %maxyear;

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
			my $author_name = trim($n);
			my $author_email = trim($e);
			my $who = $author_name;
			$who .= " $author_email" if $author_email;
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year);
			if (my $year2 = $y2) {
				update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year2);
			}
		}
	}

	if ($old_file eq 'LICENSE') {
	} else {
		# add implicit copyright statements from authors found in git blame
		my (%line_counts, %line_authors);
		my ($last_commit, $author_name, $author_email);
		my @blame_args = ('git', 'blame', "-L$lineno,", '-C', '-w', '-p');
		push(@blame_args, $ENV{'GIT_COMMIT'}) if $ENV{'GIT_COMMIT'};
		push(@blame_args, '--', $old_file);
		open( B, '-|', @blame_args);
		while (<B>) {
			chop;
			if (/^([0-9a-f]{40}) \d+ \d+ (\d+)$/) {
				$last_commit = $1;
				$line_counts{$1} += $2;
				next;
			}
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
				my $who = "$author_name $author_email";
				next if $who eq 'Not Committed Yet <not.committed.yet>';
				my @tm = localtime($1);
				my $year = $tm[5] + 1900;
				my $mon = $tm[4] + 1;
				$who = translate_author($who, $year, $mon);
				$line_authors{$last_commit} = [$who, $year, $mon];
			}
		}
		close B;

		my %author_linecounts;
		foreach $last_commit (keys %line_counts) {
			my $who = $line_authors{$last_commit}[0];
			next unless $who;
			$author_linecounts{$who} += $line_counts{$last_commit};
		}

		my $sz = 100;
		my $count_big = 0;
		foreach (values %author_linecounts) {
			$count_big++ if $_ >= $sz;
		}

		my $added_count = 0;
		foreach (values %line_authors) {
			my ($who, $year, $mon) = @$_;
			next if ($count_big && $author_linecounts{$who} < $sz);
			$all_years{$year} = 1;
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year, $mon);
		}
	}

	# rewrite file
	open( O, '>', $new_file );
	print O $top;

	my %used_author;
	foreach my $year ( sort { $a cmp $b } keys %all_years ) {
		foreach my $who ( sort keys %author_years ) {
			next if $used_author{$who}++;
			local $_ = format_copyright($minyear{$who}, $maxyear{$who}, $who);
			&{$fmt}();
			print O;
		}
	}

	local $_ = $license_text;
	&{$fmt}();
	print O;
	print O $btm;
	print O "\n";
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
		return ($a_mon <=> $b_mon);
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
		next if (date_cmp($year, $mon, $spec->[1], $spec->[2]) < 0);
		next if (date_cmp($year, $mon, $spec->[3], $spec->[4]) > 0);
		return $spec->[5];
	}
	return $who;
}

sub format_copyright {
	my ($minyear, $maxyear, $who) = @_;
	if ($minyear < $maxyear) {
		return " Copyright (C) $minyear-$maxyear, $who\n";
	} else {
		return " Copyright (C) $minyear, $who\n";
	}
}

