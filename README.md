# ScroogeCoin

This repository contains a small ScroogeCoin implementation and a set of JUnit tests located in `test/`.
The `run-tests.sh` script automates downloading the test dependencies (JUnit/Hamcrest), compiling the project, and running the tests.

Prerequisites
- JDK (javac + java) installed and available in your PATH. Check with:
  ```bash
  javac -version
  java -version
  ```
- Internet access (only required the first time to download the JUnit/Hamcrest jars).

Quick start
1. Make the script executable (once):
   ```bash
   chmod +x ./run-tests.sh
   ```
2. Run the script (compiles and runs the default `IsValidTest` suite):
   ```bash
   ./run-tests.sh
   ```
3. Run a specific test class (pass the class name):
   ```bash
   ./run-tests.sh TestClassName
   ```
4. Clean the build (remove `bin/`):
   ```bash
   ./run-tests.sh clean
   ```

What the script does
- Creates `lib/` and `bin/` if necessary.
- Downloads `junit-4.13.2.jar` and `hamcrest-core-1.3.jar` into `lib/` if they are missing.
- Compiles all `.java` files from the project root and `test/` into `bin/`.
- Runs the JUnit runner (`org.junit.runner.JUnitCore`) for the provided test class (defaults to `IsValidTest`).

Useful files
- `run-tests.sh` — automation script (compile + run tests)
- `test/IsValidTest.java` — JUnit test suite with 7 tests
- `test/UtxoTestSet.java` and `test/ValidationLists.java` — test helpers
