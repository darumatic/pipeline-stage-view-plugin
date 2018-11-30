# Pipeline Stage View Plugin

This plugin is a fork of Jenkins Pipeline Stage View Plugin, a graphical swimlane view of pipeline stage execution (as well as a build history of the stages). 

See the [Plugin Wiki](https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Stage+View+Plugin) for release information and documentation. 

## Stage View Enhancement: 

- Display last commit branch name
- Display last commit author, description
- Display change set total commits
- Display change set commits history with popup
- Display ENVIRONMENT
- Pipeline promote build support

<img src="doc/pipeline-stage-view.png"/>

## How to install

1. Download `pipeline-rest-api.jpi`, `pipeline-promote-api.jpi`, `pipeline-stage-view.jpi` from releases.
2. Upload the 3 Jenkins plugin files to Jenkins plugins directory, for Ubuntu, the directory should be `/var/lib/jenkins/plugins`
3. Restart Jenkins

> Note: If pipeline-promote-api.jpi not installed. It will disable the promote function from pipeline-state-view. 

## pipeline-promote plugin

Pipeline-promote plugin is used to promote success build to other environments. The plugin only logs Git commit hash and promote related variables. 
Pipeline script will use these variables to test if current build is promote build, and checkout corresponding commit. 

- It supports 3 preset environments: SIT/UAT/PROD. 
- It logs each build's Git commit hash as variables for each SCM
- For promote build, it logs PROMOTE_FROM_ENVIRONMENT and PROMOTE_FROM_VERSION variables.

### How to use it

1. Create a new pipeline job
2. Select "This project is parameterized", add String parameter ENVIRONMENT
3. Config pipeline with SCM
<img src="doc/job-config.png"/>
4. Edit Jenkinsfile to test promote build. An example:

> NOTE: 
> 1. SCM in Jenkinsfile must has a name. So we could save its git commit hash to variable $NAME_GIT_COMMIT

```
pipeline {
    agent any

    stages {
        stage('Checkout Project2.') {
            steps {
                script {
                    if (params.PROMOTE_FROM_ENVIRONMENT != null) {
                        checkout([$class: 'GitSCM',
                        		branches: [[name: params.PROJECT2_GIT_COMMIT]],
                        		userRemoteConfigs: [[url: 'https://github.com/Chi996/jenkins-test-project2.git', name:'project2']]])
                    }else{
                        checkout([$class: 'GitSCM',
                                 branches: [[name: params.ENVIRONMENT]],
                                 userRemoteConfigs: [[url: 'https://github.com/Chi996/jenkins-test-project2.git', name:'project2']]])
                    }
                }
            }
        }
        stage('Build Project2') {
             steps {
                 sh 'ls -al'
             }
        }
        stage('Checkout Project3') {
            steps {
                script {
                    if (params.PROMOTE_FROM_ENVIRONMENT != null) {
                        checkout([$class: 'GitSCM',
                                branches: [[name: params.PROJECT3_GIT_COMMIT]],
                                userRemoteConfigs: [[url: 'https://github.com/Chi996/jenkins-test-project3.git', name:'project3']]])
                    }else{
                        checkout([$class: 'GitSCM',
                                 branches: [[name: params.ENVIRONMENT]],
                                 userRemoteConfigs: [[url: 'https://github.com/Chi996/jenkins-test-project3.git', name:'project3']]])
                    }
                }
            }
        }
        stage('Build Project3') {
             steps {
                sh 'ls -al'
             }
        }
        stage('Print params'){
            steps {
                echo "ENVIRONMENT: ${params.ENVIRONMENT}"
                echo "PROMOTE_FROM_ENVIRONMENT: ${params.PROMOTE_FROM_ENVIRONMENT}"
                echo "PROMOTE_FROM_VERSION: ${params.PROMOTE_FROM_VERSION}"

                echo "ROOT_GIT_COMMIT: ${params.ROOT_GIT_COMMIT}"
                echo "PROJECT2_GIT_COMMIT: ${params.PROJECT2_GIT_COMMIT}"
                echo "PROJECT3_GIT_COMMIT: ${params.PROJECT3_GIT_COMMIT}"
            }
        }
    }
}
```










