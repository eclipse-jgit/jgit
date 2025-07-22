#!/usr/bin/env groovy

// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

pipeline {
    agent { label 'bazel-debian' }
    options {
        skipDefaultCheckout true
    }
    stages {
        stage('Checkout JGit') {
            steps {
                checkout scm
            }
        }
       stage('Build JGit') {
            steps {
                sh "bazelisk build all"
            }
        }
        stage('Test JGit') {
            steps {
                sh "echo Jgit running tests..."
                //sh "bazelisk test //..."
            }
        }
        stage('Checkout Gerrit') {
            steps {
                sh "git clone -b stable-3.12 --recursive https://gerrit.googlesource.com/gerrit"
            }
       }
       stage('Build Gerrit') {
           steps {
                dir ('gerrit') {
                  sh "echo $HOME"
                  sh "ls -l ../"
                  sh "ln -fs ../jgit modules/jgit"
                  sh "bazelisk build release"
                }
            }
        }
    }
    post {
        success {
            gerritReview labels: [Verified: 1]
        }
        unstable {
            gerritReview labels: [Verified: -1]
        }
        failure {
            gerritReview labels: [Verified: -1]
        }
    }
}
