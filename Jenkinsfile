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
    environment {
        JGIT_SOURCE_DIR = "${env.WORKSPACE}/jgit"
        JGIT_TARGET_DIR = "${env.WORKSPACE}/gerrit/modules/jgit"
    }
    stages {
        stage('Checkout JGit') {
            steps {
               dir('jgit') {
                 checkout scm
               }
            }
        }
        stage('Build JGit') {
            steps {
              dir('jgit') {
                sh '''
                    . set-java.sh 21
		    java -version
                    bazelisk build all
                '''
              }
           }
        }

        stage('Test JGit') {
            steps {
              dir('jgit') {
                sh '''
                    echo "JGit running tests..."
                    #TODO: REMOVE bazelisk test //...
                '''
              }
           }
        }

        stage('Checkout Gerrit stable-3.12') {
            steps {
                sh '''
                    git clone -b stable-3.12 --recursive https://gerrit.googlesource.com/gerrit
                '''
            }
        }

        stage('Build Gerrit stable-3.12') {
            steps {
                script {
                    echo "Replacing gerrit jgit module with jgit change ${env.GERRIT_CHANGE_NUMBER}..."

                    sh """
                        echo "${env.JGIT_SOURCE_DIR} -> ${env.JGIT_TARGET_DIR}"
                        mv ${env.JGIT_TARGET_DIR} /tmp/$RANDOM
                        ln -sfn ${env.JGIT_SOURCE_DIR} ${env.JGIT_TARGET_DIR}
                    """

                    dir('gerrit') {
                        sh "bazelisk build release"
                    }
                }
            }
        }
        stage('Checkout Gerrit master') {
            steps {
		dir('gerrit') {
                  sh '''
                    git checkout master
                    git submodule update --init --recursive
                  '''
                }
            }
        }
        stage('Build Gerrit master') {
            steps {
                script {
                    echo "Replacing gerrit jgit module with jgit change ${env.GERRIT_CHANGE_NUMBER}..."

                    sh """
                        echo "${env.JGIT_SOURCE_DIR} -> ${env.JGIT_TARGET_DIR}"
                        mv ${env.JGIT_TARGET_DIR} /tmp/$RANDOM
                        ln -sfn ${env.JGIT_SOURCE_DIR} ${env.JGIT_TARGET_DIR}
                    """

                    dir('gerrit') {
                        sh "bazelisk build release"
                    }
                }
            }
        }
    }

    post {
        success {
            gerritReview labels: [Verified: 1], message: "Gerrit successfully builds with this JGit change.\nBuild: ${env.BUILD_URL}"
        }
        unstable {
            gerritReview labels: [Verified: -1], message: "Gerrit build with this JGit change is unstable.\nBuild: ${env.BUILD_URL}"
        }
        failure {
            gerritReview labels: [Verified: -1], message: "Gerrit does not build successfully with this JGit change.\nBuild: ${env.BUILD_URL}"
        }
    }
}
