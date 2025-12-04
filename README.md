<img src="./atuLogo2.jpg" height="200" align="centre"/>

# ATU Cloud Native Computing

# Lab: **Extending CI/CD — Package Stage**

## Lab Objectives

In this lab you'll:

- Extend your commit stage pipeline to include container image packaging
- Push container images to GitHub Container Registry
- Implement image tagging strategies for traceability


## Introduction

In a previous lab, you created a **commit stage** pipeline for the catalog-service. That pipeline builds your code, runs tests, scans for vulnerabilities with Anchore, and submits dependency information to GitHub.

The commit stage answers: *"Does the code work and is it safe?"*

Now we need to answer: *"Can we package it for deployment?"*

In this lab, you'll add a **package job** to your existing workflow that builds a container image and pushes it to GitHub Container Registry.

## Prerequisites

This repo contains:
  - the `catalog-service` project source code
  - the commit stage workflow (`.github/workflows/commit-stage.yml`)
  - a `Dockerfile` for the catalog-service project
  - a Kubernetes `deployment.yaml` file for the catalog-service project

## Part 1: Review Your Current Pipeline

Open your `.github/workflows/commit-stage.yml`. You should have two jobs: `build` (which compiles, tests, and scans) and `dependency-submission`.

Go to your repository's **Actions** tab on GitHub and verify the commit stage is working. You should see successful runs from previous pushes.

## Part 2: Configure Repository Permissions

Before we can push images, we need to enable package write permissions.

1. Go to your repository on GitHub

2. Click **Settings** → **Actions** → **General**

3. Scroll to **Workflow permissions**

4. Select **Read and write permissions**

5. Click **Save**

## Part 3: Add the Package Job

We'll add a new job to your existing `commit-stage.yml` that builds and pushes a container image. This job will only run on pushes to main (not on pull requests) and only after the build job succeeds.

Open `.github/workflows/commit-stage.yml` and add the following at the top, after the `on:` block:

These environment variables configure the container registry (GitHub Container Registry) and set the image name. **Replace `USERNAME` with your GitHub username** (e.g., if your username is `john_doe`, use `catalog-service-john_doe`). All images will be stored in the `COMP09034-25` organization repository, and the unique image name ensures you can identify your container among other students' images.

```yaml
env:
  REGISTRY: ghcr.io
  IMAGE_NAME: comp09034-25/catalog-service-USERNAME
```

Then add this new job at the end of the file, after the `dependency-submission` job:

```yaml
  package:
    name: Package and Publish
    needs: build
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    
    permissions:
      contents: read
      packages: write
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582
      
      - name: Build JAR
        run: ./gradlew bootJar
      
      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Extract metadata for Docker
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=
            type=raw,value=latest
      
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

### Complete Workflow File

Your complete `commit-stage.yml` should now look like this:

```yaml
name: Commit Stage

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
  workflow_dispatch:

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: comp09034-25/catalog-service-USERNAME

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      actions: read
      contents: read
      security-events: write

    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin' 
        
    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582

    - name: Build with Gradle Wrapper
      run: ./gradlew build

  dependency-submission:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Generate and submit dependency graph
      uses: gradle/actions/dependency-submission@af1da67850ed9a4cedd57bfd976089dd991e2582

  package:
    name: Package and Publish
    needs: build
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    
    permissions:
      contents: read
      packages: write
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@af1da67850ed9a4cedd57bfd976089dd991e2582
      
      - name: Build JAR
        run: ./gradlew bootJar
      
      - name: Log in to Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Extract metadata for Docker
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=
            type=raw,value=latest
      
      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

## Part 4: Understanding the Package Job

Let's examine the key elements of the package job.

### Job Dependencies and Conditions

```yaml
needs: build
if: github.event_name == 'push' && github.ref == 'refs/heads/main'
```

The `needs: build` ensures the package job only runs after the build job succeeds. If tests fail or vulnerabilities are found, packaging is skipped.

The `if` condition ensures we only package on pushes to main — not on pull requests. There's no point creating container images for code that hasn't been merged yet.

### Registry Authentication

```yaml
- name: Log in to Container Registry
  uses: docker/login-action@v3
  with:
    registry: ${{ env.REGISTRY }}
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

The `GITHUB_TOKEN` is automatically provided by GitHub Actions — no need to create secrets manually. The `packages: write` permission we declared allows this token to push images.

### Image Tagging

```yaml
tags: |
  type=sha,prefix=
  type=raw,value=latest
```

We create two tags for each image:

- **Commit SHA** (e.g., `abc123f`): Provides exact traceability to the code
- **`latest`**: Convenient for development, but avoid using in production

## Part 5: Push and Observe

Commit and push your changes:

```bash
git add .github/workflows/commit-stage.yml
git commit -m "Add package job to build container images"
git push
```

Go to the **Actions** tab on GitHub and watch the workflow run:

1. The `build` job runs first (compile, test, vulnerability scan)
2. The `dependency-submission` job runs in parallel
3. Once `build` succeeds, the `package` job starts
4. The container image is built and pushed to the registry

## Part 6: Verify Your Image

### Find the Image on GitHub

1. Go to the COMP09034-25 organization on GitHub: `github.com/orgs/COMP09034-25/packages`

2. Find your `catalog-service-USERNAME` package (where USERNAME is your GitHub username)

3. Click on your package to see details

4. Note the tags: a commit SHA and `latest`

### Pull and Run Locally

```bash
# Log in to GitHub Container Registry
echo "YOUR_PERSONAL_ACCESS_TOKEN" | docker login ghcr.io -u YOUR_USERNAME --password-stdin
```

#### To find your GitHub Personal Access Token:
- Go to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
- Click Generate new token
- Select permissions: write:packages, read:packages
- Copy the token immediately (you won't see it again)
- Use it in place of YOUR_PERSONAL_ACCESS_TOKEN above
Note: Never commit tokens to git. Treat them like passwords.

```bash
# Pull the image (replace USERNAME with your GitHub username)
docker pull ghcr.io/comp09034-25/catalog-service-USERNAME:latest

# Run it (replace USERNAME with your GitHub username)
docker run -p 9001:9001 ghcr.io/comp09034-25/catalog-service-USERNAME:latest

# Test it (in another terminal)
curl localhost:9001/

# Clean up - press Ctrl+C in the terminal running the container to stop it
```

Replace `YOUR_USERNAME` with your GitHub username in the commands above. You'll need a Personal Access Token with `read:packages` scope for local pulls.

## Part 7: Test the PR Workflow

Verify that the package job correctly skips on pull requests.

1. Create a feature branch:

   ```bash
   git checkout -b feature/test-pr
   ```

2. Make a small change (e.g., update the greeting in `HomeController.java`)

3. Commit and push:

   ```bash
   git add .
   git commit -m "Test PR workflow"
   git push -u origin feature/test-pr
   ```

4. Create a Pull Request on GitHub

5. Watch the Actions tab:
   - `build` job runs (tests and scans your code)
   - `dependency-submission` job runs
   - `package` job is **skipped** (shown as greyed out)

6. Merge the PR

7. Watch the Actions tab again:
   - All three jobs run
   - A new container image is pushed with the merge commit SHA

8. Clean up:

   ```bash
   git checkout main
   git pull
   git branch -d feature/test-pr
   ```

## Part 8: Image Tagging Strategies

Understanding tagging strategies is important for production deployments.

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| `latest` | `app:latest` | Convenient | No traceability, risky in production |
| Semantic version | `app:v1.2.3` | Meaningful versions | Requires manual management |
| Commit SHA | `app:abc123f` | Exact traceability | Not human-friendly |
| Branch + SHA | `app:main-abc123f` | Context + traceability | Longer tags |

Our workflow uses both `latest` (convenience) and commit SHA (traceability). In production, always reference images by their SHA tag.

### Why Commit SHA Tags Matter

If there's a bug in production:

1. Check what image tag is deployed (e.g., `abc123f`)
2. Find that exact commit in Git: `git show abc123f`
3. See exactly what code is running
4. Reproduce the issue locally with that specific image

This traceability is impossible with only `latest`.

## Part 9: The Pipeline So Far

Your pipeline now implements:

```
┌─────────────────────────────────────────────────────────┐
│                     Commit Stage                         │
├─────────────────────────┬───────────────────────────────┤
│         Build           │           Test                │
│                         │                               │
└─────────────────────────┴───────────────────────────────┘
                            │
                            ▼ (only on main, if build passes)
┌─────────────────────────────────────────────────────────┐
│                    Package Stage                         │
├─────────────────────────────────────────────────────────┤
│  Build JAR → Build Image → Tag → Push to Registry       │
└─────────────────────────────────────────────────────────┘
```

This embodies the **Build, Release, Run** principle from the 12-Factor App methodology:

- **Build**: Compile code, run tests, produce JAR
- **Release**: Package JAR into container image with runtime environment
- **Run**: Deploy the image (covered in later labs)

The same immutable image can be deployed to any environment — development, staging, production. Only configuration changes between environments.

## Conclusion

You've extended your CI/CD pipeline to include automated container image packaging. Your workflow now:

- Builds and tests code on every push and PR
- Scans for vulnerabilities before code can progress
- Packages successful main branch builds as container images
- Pushes images to GitHub Container Registry with traceable tags

Any image in your registry has passed all tests and security scans — it's ready for deployment.

## Next: GitOps Deployment

The next step is to learn how to automatically deploy your containerized application to Kubernetes using GitOps principles.

---
