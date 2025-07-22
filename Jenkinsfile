pipeline {
    agent { label 'bazel-debian' }
    options {
        skipDefaultCheckout true
    }
    stages {
        stage('Checkout') {
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
                sh "bazelisk test //..."
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
