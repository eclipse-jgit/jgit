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
			sh "pwd && ls -l"
                        sh "bazelisk build all"
                        sh "bazelisk test //..."
                    }
            }
        }
    }
