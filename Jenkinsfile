        pipeline {
            options { skipDefaultCheckout true }
            agent { label 'bazel-debian' }
            stages {
                stage('Checkout') {
                    steps {
                            checkout scm
                        }

                }
                stage('build') {
                    steps {
                        dir ('jgit') {
                            sh "bazelisk build all"
                            sh "bazelisk test //..."
                        }
                    }
            }
        }
    }
