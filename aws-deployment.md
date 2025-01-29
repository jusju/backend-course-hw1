# AWS and GitHub Actions CI/CD with OIDC

keywords: AWS, GitHub Actions, CI/CD, Spring, Thymeleaf, Docker, AWS ECR, AWS ECS Fargate, OIDC

Made using Peter Sankauskas presentation ["CI/CD: GitHub Actions to ECS"](https://www.youtube.com/watch?v=kHYZX3-EQaw&list=PLOqPCHMU4D8IhUFOeY7DHXWtNVAsRZM4q&index=2)

---

### Steps needed to CI/CD

1. Push image to ECR repository
2. Read current ECS Task Definition the ECS Service is using
3. Create and register a new ECS Task Definition using the new image
4. Update ECS Service to use new Task Definition

Before we can execute these steps we need to set up our AWS services.

### Steps needed to set up AWS services 

1. Create an ECR repository
2. Push docker image to the repository
3. Create an ECS Cluster
4. Create a Task Definition
5. Create a Service
6. Create an OIDC provider
7. Create an IAM role


## Setting up AWS

#### Create an ECR repository

![img.png](images/aws-deployment/create-ecr-repository1.png)

![img.png](images/aws-deployment/create-ecr-repository2.png)

#### View push commands and follow them*

![img.png](images/aws-deployment/view-push-commands.png)
*This step is required as part of the initial setup to enable the creation of the task definition and service.

#### Create an ECS cluster

![img.png](images/aws-deployment/create-cluster1.png)

![img.png](images/aws-deployment/create-cluster2.png)

#### Create a Task Definition

![img.png](images/aws-deployment/create-task-definition1.png)

![img.png](images/aws-deployment/create-task-definition2.png)
![img.png](images/aws-deployment/create-task-definition3.png)

Set login how you prefer (if not selected log panel will be empty) and leave all other settings as default. 


#### Create a Service

![img.png](images/aws-deployment/create-service1.png)

![img.png](images/aws-deployment/create-service2.png)
![img.png](images/aws-deployment/create-service3.png)
![img.png](images/aws-deployment/create-service4.png)

Leave all other settings as default.

#### Create an OIDC provider

![img.png](images/aws-deployment/identity-provider1.png)

![img.png](images/aws-deployment/identiety-provider2.png)

#### Create an IAM role

![img.png](images/aws-deployment/create-role1.png)
Select Assign role

![img.png](images/aws-deployment/create-role2.png)

![img.png](images/aws-deployment/create-role3.png)

![img.png](images/aws-deployment/create-role4.png)

![img.png](images/aws-deployment/create-role5.png)

![img.png](images/aws-deployment/create-role6.png)

![img.png](images/aws-deployment/create-role7.png)
We need Service arn

![img.png](images/aws-deployment/create-role8.png)
We also need "executionRoleArn" from Services Task definition AND if we have "taskRoleArn", we also need that.

![img.png](images/aws-deployment/create-role9.png)
![img.png](images/aws-deployment/create-role10.png)


```
{
	"Version": "2012-10-17",
	"Statement": [
        {
            "Sid": "ManageTaskDefinitions",
            "Effect": "Allow",
            "Action": [
                "ecs:DescribeTaskDefinition",
                "ecs:RegisterTaskDefinition"
            ],
            "Resource": "*"
        },
        {
            "Sid": "DeployService",
            "Effect": "Allow",
            "Action": [
                "ecs:DescribeServices",
                "ecs:UpdateService"
            ],
            "Resource": [
                # This is an example! You should but your own services here!
                "arn:aws:ecs:eu-north-1:571600864498:service/CleanCluster/clean-service"
                # Add more services here 
            ]
        },
        {
            "Sid": "PassRolesInTaskDefinition",
            "Effect": "Allow",
            "Action": [
                "iam:PassRole"
            ],
            "Resource": [
                # This is an example! You should but your own Task definition arns here 
                "arn:aws:iam::571600864498:role/ecsTaskExecutionRole"
                
                # copy from task Definition: 
                # "executionRoleArn": ...
                # "taskRoleArn": ...
                # (it is possible that you don't have taskRoleArn)  
            ]
        }
	]
}
```

![img.png](images/aws-deployment/create-role11.png)

![img.png](images/aws-deployment/create-role12.png)

![img.png](images/aws-deployment/create-role13.png)

![img.png](images/aws-deployment/create-role14.png)


## Setting up CI/CD in GitHub

### Example Dockerfile

```
FROM eclipse-temurin:23

WORKDIR /app

#COPY build/libs/project-name-0.0.1-SNAPSHOT.jar app.jar
COPY build/libs/*.jar app.jar

CMD ["java", "-jar", "app.jar"]
```

### Example image-tag script 

```
#!/bin/bash

set -e

NOW=$(date +'%Y%m%d-%H%M%S')
SHORT_SHA=$(git rev-parse --short HEAD)
BRANCH=$(git rev-parse --abbrev-ref HEAD | sed -re 's/[^a-z0-9-]/-/g' | cut -c1-30 | sed -re 's/-+/-/g' | sed -re 's/-$//')

# The image tag will be in the format:
#  - GitHub PR:             pr-123.YYYMMDD-HHMMSS.SHA0123.ci
#  - GitHub merge to main:  BRANCH.YYYMMDD-HHMMSS.SHA0123.ci
#  - Local main:            BRANCH.YYYMMDD-HHMMSS.SHA0123.local
#  - Local branch:          BRANCH.YYYMMDD-HHMMSS.SHA0123.local

if [[ "$GITHUB_REF" == refs/pull* ]]; then
  IMAGE_TAG_PREFIX=pr-$(echo "$GITHUB_REF" | cut -d'/' -f3)
else
  IMAGE_TAG_PREFIX=$BRANCH
fi

if [[ "$CI" == true ]]; then
  IMAGE_TAG_SUFFIX=ci
else
  IMAGE_TAG_SUFFIX=local
fi

IMAGE_TAG="${IMAGE_TAG_PREFIX}.${NOW}.${SHORT_SHA}.${IMAGE_TAG_SUFFIX}"

echo "$IMAGE_TAG"
```

### GitHub Actions template

```
name: Deploy to Amazon ECS

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:

  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read

    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 23
        uses: actions/setup-java@v4
        with:
          java-version: '23'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0

      - name: Build with Gradle Wrapper
        run: ./gradlew build



  make_image:
    name: Build, tag & push image to ECR
    runs-on: ubuntu-latest
    environment: production

    # Only run this job if the CI job was successful
    # TODO - This is here only for example. Job build is basicly reduntant here because building with gradlew happens also in this make_image job
    needs: [build]

    # Do not run on dependabot PRs or any PRs
    if: ${{ github.event_name != 'pull_request' }}
    # if: ${{ github.actor != 'dependabot[bot]' }}

    # Permissions needed for OIDC
    # See: https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/about-security-hardening-with-openid-connect#adding-permissions-settings
    permissions:
      contents: read
      id-token: write

    # Outputs to be used by other jobs
    outputs:
      image_tag: ${{ steps.image_tag.outputs.image_tag }}
      image: ${{ steps.full_tag.outputs.image }}

    steps:
      - name: Checkout code
        if: ${{ github.event_name != 'pull_request' }}
        uses: actions/checkout@v4

      - name: Checkout code (PR version)
        if: ${{ github.event_name == 'pull_request' }}
        uses: actions/checkout@v4
        with:
          # PRs do a merge commit before running the workflow, so we need to check out the code without that.
          # See: https://github.com/actions/checkout/issues/426
          ref: ${{ github.event.pull_request.head.sha }}


      - name: Set up JDK 23
        uses: actions/setup-java@v4
        with:
          java-version: '23'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582 # v4.0.0

      - name: Build with Gradle Wrapper
        run: ./gradlew build


      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          audience: sts.amazonaws.com
          aws-region: eu-north-1
          role-to-assume: arn:aws:iam::571600864498:role/github-oidc-provider-aws-clean-thymeleaf # TODO - Must match name of IAM Role

      - name: Login to Amazon ECR
        id: login_ecr
        uses: aws-actions/amazon-ecr-login@v2


      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Cache Docker layers
        uses: actions/cache@v4
        with:
          path: /tmp/.buildx-cache
          key: ${{ runner.os }}-buildx-${{ github.sha }}
          restore-keys: ${{ runner.os }}-buildx-

      - name: Calculate image tag
        id: image_tag
        run: |
          chmod +x image-tag.sh
          echo "image_tag=$(./image-tag.sh)" >> $GITHUB_OUTPUT

      - name: Clean up commit message
        id: commit
        run: echo "message=$(git log -1 --pretty=%B | head -1)" >> $GITHUB_OUTPUT

      - name: Create the full tag
        id: full_tag
        env:
          ECR_REGISTRY: ${{ steps.login_ecr.outputs.registry }}
          ECR_REPOSITORY: practice2/clean-thymeleaf # TODO - Change this to your ECR repository
          IMAGE_TAG: ${{ steps.image_tag.outputs.image_tag }}
        run: |
          echo "image=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG" >> $GITHUB_OUTPUT

      - name: Build, tag and push image to Amazon ECR
        uses: docker/build-push-action@v6
        with:
          context: ./
          push: true
          tags: ${{ steps.full_tag.outputs.image }}
          cache-from: type=local,src=/tmp/.buildx-cache
          cache-to: type=local,dest=/tmp/.buildx-cache-new,mode=max
          platforms: linux/arm64 # TODO - remove this line if you are not using arm64 in aws

      - # Temp fix
        # https://github.com/docker/build-push-action/issues/252
        # https://github.com/moby/buildkit/issues/1896
        name: Move cache to avoid growing forever
        run: |
          rm -rf /tmp/.buildx-cache
          mv /tmp/.buildx-cache-new /tmp/.buildx-cache

  deploy:
    name: Deploy to ECS
    runs-on: ubuntu-latest
    needs: [make_image]
    environment: production
    concurrency: production

    # Permissions needed for OIDC
    permissions:
      contents: read
      id-token: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Configure AWS Credentials
        uses: aws-actions/configure-aws-credentials@v4
        with:
          audience: sts.amazonaws.com
          aws-region: eu-north-1 # TODO - AWS region
          role-to-assume: arn:aws:iam::571600864498:role/github-oidc-provider-aws-clean-thymeleaf # TODO - Must match name of IAM Role

      - name: Download task definition
        # TODO - Task definition (... --task-definition your-task-definition \)
        run: |
          aws ecs describe-task-definition --task-definition clean-task-definition \
          --query taskDefinition > /tmp/app-task-definition.json

      - name: Fill in the new image ID in the Amazon ECS task definition
        id: task-def-app
        uses: aws-actions/amazon-ecs-render-task-definition@v1
        with:
          task-definition: /tmp/app-task-definition.json
          container-name: clean # TODO - Must match name of container in Task definition ("containerDefinitions": [{"name": "container-name", ...}]
          image: ${{ needs.make_image.outputs.image }}
          docker-labels: |
            SERVICE=clean
            VERSION=${{ steps.image_tag.outputs.image_tag }}

      - name: Deploy Amazon ECS task definition workers
        id: deploy-workers
        uses: aws-actions/amazon-ecs-deploy-task-definition@v2
        with:
          task-definition: ${{ steps.task-def-app.outputs.task-definition }}
          cluster: CleanCluster # TODO - Cluster name ( cluster and service are also defined in the policy: "arn:aws:ecs:eu-north-1:571600864498:service/CleanCluster/clean-service")
          service: clean-service # TODO - Service name
          wait-for-service-stability: true
          wait-for-minutes: 10

```


