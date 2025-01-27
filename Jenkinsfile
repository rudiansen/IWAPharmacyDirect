#!/usr/bin/env groovy

//****************************************************************************************************
// An example pipeline for DevSecOps using Micro Focus Fortify Application Security products
// @author: Kevin A. Lee (kevin.lee@microfocus.com)
//
//
// Pre-requisites:
// - Fortify SCA/ScanCentral SAST has been installed (for on-premise SAST)
// - Fortify WebInspect/ScanCentral DAST has been installed (for on-premise DAST)
// - A Fortify On Demand account and API access are available (for cloud based SAST/DAST)
// - Docker Pipeline plugin has been installed (Jenkins)
// - [Optional] Sonatype Nexus IQ server has been installed for OSS SCA vulnerabilities
//
// Typical node setup:
// - Install Fortify SCA on the agent machine
// - create a new Jenkins agent for this machine
// - Apply the label "fortify" to the agent.
// - Set the environment variable "FORTIFY_HOME" on the agent point to the location of the Fortify SCA installation
// Also ensure the label "master" has been applied to your Jenkins master.
//
// Credentials setup:
// Create the following "Secret text" credentials in Jenkins and enter values as follows:
//		iwa-git-creds-id		    - Git login as Jenkins "Username with Password" credential
//      iwa-ssc-ci-token-id         - Fortify Software Security Center "CIToken" authentication token as Jenkins Secret credential
//      iwa-edast-auth-id           - Fortify Scan Central DAST authentication as "Jenkins Username with Password" credential
//      iwa-nexus-iq-token-id       - Sonatype Nexus IQ user token in form of "user code:pass code"
//      iwa-dockerhub-creds-id      - DockerHub login as Jenkins "Username with Password" credential
// All of the credentials should be created (with empty values if necessary) even if you are not using the capabilities.
// For Fortify on Demand (FOD) Global Authentication is used rather than Personal Access Tokens.
//
//****************************************************************************************************

// The instances of Docker image and container that are created
def dockerImage
def dockerContainer
def dockerContainerName = "iwa-jenkins"

pipeline {
    agent any

    //
    // The following parameters can be selected when the pipeline is executed manually to execute
    // different capabilities in the pipeline or configure the servers that are used.

    // Note: the pipeline needs to be executed at least once for the parameters to be available
    //
    parameters {        
        booleanParam(name: 'SCANCENTRAL_SAST', 	defaultValue: params.SCANCENTRAL_SAST ?: false,
                description: 'Run a remote scan using Scan Central SAST (SCA) for Static Application Security Testing')        
        booleanParam(name: 'UPLOAD_TO_SSC',		defaultValue: params.UPLOAD_TO_SSC ?: false,
                description: 'Enable upload of scan results to Fortify Software Security Center')        
        booleanParam(name: 'USE_DOCKER', defaultValue: params.USE_DOCKER ?: false,
                description: 'Package the application into a Dockerfile for running/testing')
        booleanParam(name: 'RELEASE_TO_NEXUSREPO', defaultValue: params.RELEASE_TO_NEXUSREPO ?: false,
                description: 'Release built and tested image to Nexus Repository')
    }

    environment {
        // Application settings
        APP_NAME = "IWA"                      		        // Application name
        APP_VER = "poc-sss"                                 // Application release - GitHub poc-sss branch
        COMPONENT_NAME = "iwa"                              // Component name
        GIT_URL = scm.getUserRemoteConfigs()[0].getUrl()    // Git Repo
        JAVA_VERSION = 8                                    // Java version to compile as           

        // Credential references        
        //SSC_AUTH_TOKEN = credentials('iwa-ssc-ci-token-id')    

        // The following are defaulted and can be overriden by creating a "Build parameter" of the same name
        SSC_URL = "${params.SSC_URL ?: 'http://10.87.1.12:8080/ssc'}" // URL of Fortify Software Security Center
        SSC_APP_VERSION_ID = "${params.SSC_APP_VERSION_ID ?: '100'}" // Id of Application in SSC to upload results to
        SSC_NOTIFY_EMAIL = "${params.SSC_NOTIFY_EMAIL ?: 'rudiansen.gunawan@packet-systems.com'}" // User to notify with SSC/ScanCentral information
        SSC_SENSOR_POOL_UUID = "${params.SSC_SENSOR_POOL_UUID ?: '00000000-0000-0000-0000-000000000002'}" // UUID of Scan Central Sensor Pool to use - leave for Default Pool        
    }

    // tools {
    //     // Install the Maven version configured as "M3" and add it to the path.
    //     maven 'M3'
    // }

    stages {
        stage('Build') {            
            steps {
                // Get some code from a GitHub repository
                // git credentialsId: 'iwa-git-creds-id', url: "${env.GIT_URL}"
                git branch: 'poc-sss', url: 'https://github.com/rudiansen/IWAPharmacyDirect'

                // Get Git commit details
                script {
                    if (isUnix()) {
                        sh 'git rev-parse HEAD > .git/commit-id'
                    } else {
                        bat(/git rev-parse HEAD > .git\\commit-id/)
                    }
                    //bat(/git log --format="%ae" | head -1 > .git\commit-author/)
                    env.GIT_COMMIT_ID = readFile('.git/commit-id').trim()
                    //env.GIT_COMMIT_AUTHOR = readFile('.git/commit-author').trim()

                    println "Git commit id: ${env.GIT_COMMIT_ID}"
                    //println "Git commit author: ${env.GIT_COMMIT_AUTHOR}"

                    // Run maven to build WAR/JAR application
                    if (isUnix()) {
                        sh 'mvn "-Dskip.unit.tests=false" -Dtest="*Test,!PasswordConstraintValidatorTest,!UserServiceTest,!DefaultControllerTest,!SeleniumFlowIT" -P jar -B clean verify package --file pom.xml'
                    } else {
                        bat "mvn \"-Dskip.unit.tests=false\" Dtest=\"*Test,!PasswordConstraintValidatorTest,!UserServiceTest,!DefaultControllerTest,!SeleniumFlowIT\" -P jar -B clean verify package --file pom.xml"
                    }
                }
            }

            post {
                success {
                    // Record the test results (success)
                    junit "**/target/surefire-reports/TEST-*.xml"
                    // Archive the built file
                    archiveArtifacts "target/${env.COMPONENT_NAME}.jar,target/${env.COMPONENT_NAME}.war"
                    // Stash the deployable files
                    stash includes: "target/${env.COMPONENT_NAME}.jar,target/${env.COMPONENT_NAME}.war", name: "${env.COMPONENT_NAME}_release"
                }
                failure {
                    // Record the test results (failures)
                    junit "**/target/surefire-reports/TEST-*.xml"
                }
            }
        }

        stage('SAST') {           
            steps {
                script {
                    // Get code from Git repository so we can recompile it
                    //git credentialsId: 'iwa-git-creds-id', url: "${env.GIT_URL}"
                    git branch: 'poc-sss', url: 'https://github.com/rudiansen/IWAPharmacyDirect'

                    // Run Maven debug compile, download dependencies (if required) and package up for FOD
                    if (isUnix()) {
                        sh "mvn -Dmaven.compiler.debuglevel=lines,vars,source -DskipTests -P fortify clean verify"
                        sh "mvn dependency:build-classpath -Dmdep.regenerateFile=true -Dmdep.outputFile=${env.WORKSPACE}/cp.txt"
                    } else {
                        bat "mvn -Dmaven.compiler.debuglevel=lines,vars,source -DskipTests -P fortify clean verify"
                        bat "mvn dependency:build-classpath -Dmdep.regenerateFile=true -Dmdep.outputFile=${env.WORKSPACE}/cp.txt"
                    }

                    // read contents of classpath file
                    def classpath = readFile "${env.WORKSPACE}/cp.txt"
                    println "Using classpath: $classpath"

                    if (params.SCANCENTRAL_SAST) {

                        // set any standard remote translation/scan options
                        fortifyRemoteArguments transOptions: '',
                                scanOptions: ''

                        if (params.UPLOAD_TO_SSC) {
                            // Remote analysis (using Scan Central) and upload to SSC
                            fortifyRemoteAnalysis remoteAnalysisProjectType: fortifyMaven(buildFile: 'pom.xml'),
                                    remoteOptionalConfig: [
                                            customRulepacks: '',
                                            filterFile: "etc\\sca-filter.txt",
                                            notifyEmail: "${env.SSC_NOTIFY_EMAIL}",
                                            sensorPoolUUID: "${env.SSC_SENSOR_POOL_UUID}"
                                    ],
                                    uploadSSC: [appName: "${env.APP_NAME}", appVersion: "${env.SSC_APP_VERSION_ID}"]

                        } else {
                            // Remote analysis (using Scan Central)
                            fortifyRemoteAnalysis remoteAnalysisProjectType: fortifyMaven(buildFile: 'pom.xml'),
                                    remoteOptionalConfig: [
                                            customRulepacks: '',
                                            filterFile: "etc\\sca-filter.txt",
                                            notifyEmail: "${env.SSC_NOTIFY_EMAIL}",
                                            sensorPoolUUID: "${env.SSC_SENSOR_POOL_UUID}"
                                    ]
                        }                    
                    } else {
                        println "No Static Application Security Testing (SAST) to do."
                    }
                }
            }
        }

        // stage('SCA OSS') {
        //     when {
        //         beforeAgent true
        //         anyOf {
        //             expression { params.SCA_OSS == true }
        //         }
        //     }
        //     // Run on an Agent with "fortify" label applied
        //     agent {label "fortify"}
        //     steps {
        //         script {

        //             // nexusPolicyEvaluation advancedProperties: '',
        //             //      enableDebugLogging: false,
        //             //      failBuildOnNetworkError: true,
        //             //      iqApplication: selectedApplication('IWA'),
        //             //      iqModuleExcludes: [[moduleExclude: 'target/**/*test*.*']],
        //             //      iqScanPatterns: [[scanPattern: 'target/**/*.jar']],
        //             //      iqStage: 'develop',
        //             //      jobCredentialsId: ''

        //             // run sourceandlibscanner - needs to have been installed and in the path
        //             if (isUnix()) {
        //                 sh 'sourceandlibscanner -auto -bt mvn -bf pom.xml -bc "dependency:unpack-dependencies -Dclassifier=sources -DexcludeTransitive -DskipTests package" -sonatype -iqurl ${env.NEXUS_IQ_URL} -nexusauth ${env.NEXUS_IQ_AUTH_TOKEN} -iqappid IWA -stage build -r iqReport.json -upload -ssc ${env.SSC_URL} -ssctoken ${env.SSC_AUTH_TOKEN} -versionid ${env.SSC_APP_VERSION_ID}'
        //             } else {
        //                 def stdout = powershell(returnStdout: true, script: ".\\bin\\fortify-sourceandlibscanner.ps1 -NexusIQUrl ${env.NEXUS_IQ_URL} -NexusIQAuth ${env.NEXUS_IQ_AUTH_TOKEN} -NexusIQAppId IWA -SSCURL ${env.SSC_URL} -SSCAuthToken ${env.SSC_AUTH_TOKEN} -SSCAppVersionId ${env.SSC_APP_VERSION_ID}")
        //                 println stdout
        //                 //bat(/sourceandlibscanner -auto -bt mvn -bf pom.xml -bc "dependency:unpack-dependencies -Dclassifier=sources -DexcludeTransitive -DskipTests package" -scan -sonatype -iqurl ${env.NEXUS_IQ_URL} -nexusauth ${env.NEXUS_IQ_AUTH_TOKEN} -iqappid IWA -stage build -r iqReport.json -upload -ssc ${env.SSC_URL} -ssctoken ${env.SSC_AUTH_TOKEN} -versionid ${env.SSC_APP_VERSION_ID}/)
        //             }
        //         }
        //     }
        // }

        // stage('Deploy') {
        //     // Run on "master" node
        //     agent { label 'master' }
        //     steps {
        //         script {
        //             // unstash the built files
        //             unstash name: "${env.COMPONENT_NAME}_release"
        //             if (isUnix()) {
        //                 // Create docker image using JAR file
        //                 if (params.USE_DOCKER) {
        //                     dockerImage = docker.build "${env.DOCKER_ORG}/${env.COMPONENT_NAME}:${env.APP_VER}.${env.BUILD_NUMBER}"
        //                 }
        //             } else {
        //                 // Create docker image using JAR file
        //                 if (params.USE_DOCKER) {
        //                     dockerImage = docker.build("${env.DOCKER_ORG}/${env.COMPONENT_NAME}:${env.APP_VER}.${env.BUILD_NUMBER}", "-f Dockerfile.win .")
        //                 }
        //             }
        //         }
        //     }
        // }

        // stage('DAST') {
        //     when {
        //         beforeAgent true
        //         anyOf {
        //             expression { params.SCANCENTRAL_DAST == true }
        //             expression { params.FOD_DAST == true }
        //         }
        //     }
        //     // Run on an Agent with "docker" label applied
        //     agent {label "docker"}
        //     steps {
        //         script {
        //             if (params.SCANCENTRAL_DAST && params.USE_DOCKER) {
        //                 // check if container is still running and if so stop/remove it
        //                 if (isUnix()) {
        //                     sh(script: "docker ps -aq --filter name=iwa-jenkins > container.id")
        //                     if (fileExists('container.id')) {
        //                         def existingId = readFile('container.id').trim()
        //                         if (existingId) {
        //                             println "Found existing iwa-jenkins container id: ${existingId} ... deleting..."
        //                             sh(script: "docker stop $existingId && docker rm -f $existingId")
        //                         }
        //                     }
        //                 } else {
        //                     bat(script: "docker ps -aq --filter name=iwa-jenkins > container.id")
        //                     if (fileExists('container.id')) {
        //                         def existingId = readFile('container.id').trim()
        //                         if (existingId) {
        //                             println "Found existing iwa-jenkins container id: ${existingId} ... deleting..."
        //                             bat(script: "docker stop ${existingId} && docker rm -f ${existingId}")
        //                         }
        //                     }
        //                 }

        //                 // start docker container
        //                 println "Starting docker container ${dockerContainerName}"
        //                 dockerContainer = dockerImage.run("--name ${dockerContainerName} -p 9090:8080")

        //                 // run ScanCentral DAST scan using groovy script
        //                 println "Running ScanCentral DAST scan, please wait ..."
        //                 withCredentials([usernamePassword(credentialsId: 'iwa-edast-auth-id', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        //                     edastApi = load 'bin/fortify-scancentral-dast.groovy'
        //                     edastApi.setApiUri("${env.EDAST_URL}")
        //                     edastApi.setDebug(true)
        //                     edastApi.authenticate("${USERNAME}", "${PASSWORD}")
        //                     Integer scanId = edastApi.startScanAndWait("Jenkins initiated scan", "${env.EDAST_CICD}", 5)
        //                     String scanStatus = edastApi.getScanStatusValue(edastApi.getScanStatusId(scanId))
        //                     println "ScanCentral DAST scan id: ${scanId} - status: ${scanStatus}"
        //                 }
        //             } else if (params.FOD_DAST) {
        //                 println "DAST via FOD is not yet implemented."
        //             } else {
        //                 println "No Dynamic Application Security Testing (DAST) to do."
        //             }
        //         }
        //     }
        // }

        // An example manual release checkpoint
        // stage('Stage') {
        //     agent { label 'master' }
        //     steps {
        //         input id: 'Release',
        //                 message: 'Ready to Release?',
        //                 ok: 'Yes, let\'s go',
        //                 submitter: 'admin',
        //                 submitterParameter: 'approver'
        //     }
        // }

        // stage('Release') {
        //     agent { label 'master' }
        //     steps {
        //         script {
        //             // Example publish to Docker Hub
        //             if (params.RELEASE_TO_DOCKERHUB) {
        //                 docker.withRegistry('https://registry.hub.docker.com', 'iwa-dockerhub-creds-id') {
        //                     dockerImage.push("${env.APP_VER}.${BUILD_NUMBER}")
        //                     // and tag as "latest"
        //                     dockerImage.push("latest")
        //                 }
        //             } else {
        //                 println "No releasing to do."
        //             }
        //         }
        //     }
        // }

    }

    // post {
    //     always {
    //         script {
    //             // check if container is still running and if so stop/remove it
    //             if (params.USE_DOCKER) {
    //                 if (isUnix()) {
    //                     sh(script: "docker ps -aq --filter name=iwa-jenkins > container.id")
    //                     if (fileExists('container.id')) {
    //                         def existingId = readFile('container.id').trim()
    //                         if (existingId) {
    //                             println "Found existing iwa-jenkins container id: ${existingId} ... deleting..."
    //                             sh(script: "docker stop $existingId && docker rm -f $existingId")
    //                         }
    //                     }
    //                 } else {
    //                     bat(script: "docker ps -aq --filter name=iwa-jenkins > container.id")
    //                     if (fileExists('container.id')) {
    //                         def existingId = readFile('container.id').trim()
    //                         if (existingId) {
    //                             println "Found existing iwa-jenkins container id: ${existingId} ... deleting..."
    //                             bat(script: "docker stop ${existingId} && docker rm -f ${existingId}")
    //                         }
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }

}
