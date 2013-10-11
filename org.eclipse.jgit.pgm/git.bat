@ECHO OFF

REM Copyright (C) 2013, Kaloyan Raev <kaloyan.r@zend.com>
REM and other copyright owners as documented in the project's IP log.
REM
REM This program and the accompanying materials are made available
REM under the terms of the Eclipse Distribution License v1.0 which
REM accompanies this distribution, is reproduced below, and is
REM available at http://www.eclipse.org/org/documents/edl-v10.php
REM
REM All rights reserved.
REM
REM Redistribution and use in source and binary forms, with or
REM without modification, are permitted provided that the following
REM conditions are met:
REM
REM - Redistributions of source code must retain the above copyright
REM   notice, this list of conditions and the following disclaimer.
REM
REM - Redistributions in binary form must reproduce the above
REM   copyright notice, this list of conditions and the following
REM   disclaimer in the documentation and/or other materials provided
REM   with the distribution.
REM
REM - Neither the name of the Eclipse Foundation, Inc. nor the
REM   names of its contributors may be used to endorse or promote
REM   products derived from this software without specific prior
REM   written permission.
REM
REM THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
REM CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
REM INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
REM OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
REM ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
REM CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
REM SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
REM NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
REM LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
REM CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
REM STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
REM ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
REM ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

IF DEFINED JAVA_HOME (
	SET JAVA="%JAVA_HOME%\bin\java"
) ELSE (
	SET JAVA=java
)

%JAVA% -cp "%JGIT_CLASSPATH%" org.eclipse.jgit.pgm.Main %*
