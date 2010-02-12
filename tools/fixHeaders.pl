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

my %copyrights;

open( F, '-|', 'git ls-files' );
while (<F>) {
	chop;
	next unless m,^org.eclipse.jgit(?:.*)?/,;
	next unless m,\.java$,;

	my $old_file = $_;

	my $new_file = "$old_file.license.$$";
	my $header   = '';
	my $package  = '';

	# header is everything before package statement
	open( I, '<', $old_file );
	while (<I>) {
		if (/^package /) {
			$package = $_;
			last;
		}

		$header .= $_;
	}
	die "$old_file has no package line.\n" unless $package;

	my %all_years;
	my %author_years;
	my %minyear;
	my %maxyear;
	my $year;
	my $author_name;
	my $author_email;

	# find explicit copyright statements in sources
	my @lines = split( /\n/, $header );
	foreach my $line ( @lines ) {
		# * Copyright (c) 2008, Apple Inc.
		# * Copyright (c) 2008, Joe Developer <joe.dev@dev.org>
		# * Copyright (c) 2008, 2009 Joe Developer <joe.dev@dev.org>
		# * Copyright (c) 2005-2009 Joe Developer <joe.dev@dev.org>
		# * Copyright (c) 2008, 2009 Apple Inc.
		# * Copyright (c) 2008-2010 Apple Inc.
		# * Copyright (C) 2009-2010, Google Inc.
		if( $line =~ m/.*Copyright \(c\) ((?<y>\d{4})\s*[,-]\s*(?<y2>\d{4})?)\,?((?<n>[^\r\<\>\,]*)(?<e>\<.*\>)?)\s*/i ) {
			$year = trim($+{y});
			$author_name = trim($+{n});
			$author_email = trim($+{e});
			my $who = "$author_name $author_email";
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year);
			if (my $year2 = $+{y2}) {
				update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year2);
			}
		}
	}

	my $lineno = $.;

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
			if ($author_email == "not.committed.yet") {
				next;
			}
			$year = ( localtime($1) )[5] + 1900;
			$all_years{$year} = 1;
			my $who = "$author_name $author_email";
			update_author_info(\%minyear, \%maxyear, \%all_years, \%author_years, $who, $year);
		}
	}
	close B;

	# rewrite file
	open( O, '>', $new_file );
	print O <<'EOF';
/*
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Development License v1.0 which
 * accompanies this distribution, is reproduced below, and available at
 * http://www.eclipse.org/org/documents/edl-v10.php
 *
 * Eclipse Distribution License - v 1.0
 *
EOF

	my %used_author;
	foreach my $year ( sort { $a cmp $b } keys %all_years ) {
		foreach my $who ( sort keys %author_years ) {
			next if $used_author{$who}++;
			my $y = ( sort { $b cmp $a } keys %{ $author_years{$who} } )[0];
			my $copyright = format_copyright($minyear{$who}, $maxyear{$who}, $who);
			print O $copyright;
			$copyrights{$copyright} = 1;
		}
	}

	print O <<'EOF';
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Eclipse Foundation, Inc. nor the names of
 *       its contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

EOF
	print O $package;
	print O while <I>;
	close I;
	close O;

	rename( $new_file, $old_file );
}
close F;

# list all copyrights found
print "\ncopyrights:\n";
foreach my $c ( sort keys %copyrights ) {
	print "$c";
}

sub trim($)
{
	my $string = shift;
	$string =~ s/^\s+//;
	$string =~ s/\s+$//;
	return $string;
}

sub update_author_info()
{
	my ($minyear_ref, $maxyear_ref, $all_years_ref, $author_years_ref, $who, $year) = @_;

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

sub format_copyright() {
	my ($minyear, $maxyear, $who) = @_;
	if ($minyear < $maxyear) {
		return " * Copyright (C) $minyear-$maxyear, $who\n";
	} else {
		return " * Copyright (C) $minyear, $who\n";
	}
}

