# Contributing to Flamingock Examples

Thank you for considering contributing to the Flamingock Examples repository! We appreciate your help in making this project better. To ensure a smooth and efficient process for everyone involved, please follow the guidelines below.

## How to Contribute

### 1. Fork the Repository

To contribute to the project, first, fork the repository to your own GitHub account. This allows you to freely make changes to your copy without affecting the original project.

- Go to the [Flamingock Examples GitHub repository](https://github.com/mongock/flamingock-examples).
- Click on the **Fork** button at the top-right of the page to create your own copy of the repository.

### 2. Clone Your Fork

Once you've forked the repository, clone it to your local machine to start working on your contributions.

```shell
git clone https://github.com/YOUR_USERNAME/flamingock-examples.git
cd flamingock-examples
```

### 3. Create a New Branch
Before you start making changes, create a new branch from the master branch. This will allow you to work on your changes independently and keep the history clean.
```shell
git checkout -b feature/your-feature-name
```

### 4. Make Changes
Now, you can start working on your changes. If you're adding a new example, create a new folder for the specific technology or framework you are working with. Follow the same structure as the existing examples.

- Update the README file for your example with a description of how to use it.
- If you're fixing an issue or enhancing an example, ensure your code is well-documented.

### 5. Commit Your Changes
Once you're done, commit your changes following the [Conventional Commits specification](https://www.conventionalcommits.org).

For example:
- `feat: Add new example for MongoDB with Spring Boot`
- `fix: Correct typo in MySQL example`
- `docs: Update README for Couchbase example`

### 6. Push Your Changes
After committing your changes, push the branch to your forked repository:

```shell
git push origin feature/your-feature-name
```

### 7. Open a Pull Request (PR)
Go to the original Flamingock Examples repository and open a pull request from your fork to the master branch of the original repository.
- Provide a clear title and description of the changes you're proposing.
- If you're fixing a bug or adding a new feature, reference any related issues.

### 8. PR Review Process
Once your pull request is submitted, it will undergo the following checks:
- **Build Pipeline**: Ensure the build pipeline passes without issues.
- **GitGuardian Security Checks**: Check for any potential security vulnerabilities or secrets.
- **Review by Repository Owner**: Your PR will require at least one review from a project maintainer or owner before it can be merged.

We appreciate your patience and understanding during the review process.

### 9. Acknowledgment of Contributions
Once your pull request is approved and merged, your contribution will be acknowledged in the project’s history, and your GitHub username will be listed in the contributors list.

### 10. Merge and Celebrate!
Once your pull request is approved, the maintainer will merge your changes into the master branch. 🎉


## Code of Conduct
By contributing to this repository, you agree to adhere to the project's [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting Issues
If you encounter any bugs or issues, please open an issue in the repository. Be sure to include the following information:
- A clear description of the problem.
- Steps to reproduce the issue.
- Any relevant error messages or logs.
- The version of Flamingock you are using.

## Style Guide
We strive for clean and consistent code. Here are a few guidelines to follow:

- Follow standard Java/Kotlin code conventions.
- Use meaningful and descriptive variable/method names.
- Keep methods small and focused.
- Provide adequate comments where necessary, especially when the logic is complex.
  
## License
By contributing to this repository, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE.md).


# Thank you for helping make Flamingock better! 🙌