#!/usr/bin/env bash
# Run the wiretap test suite across the supported Spring Boot / Java matrix.
# Mirrors .github/workflows/compatibility.yml so contributors can reproduce
# the CI matrix locally before opening a PR.
#
# Override the matrix from the command line:
#   SPRING_BOOTS="3.5.14 3.4.5" JAVAS="17 21" ./scripts/test-compatibility.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

SPRING_BOOTS=${SPRING_BOOTS:-"3.2.7 3.4.5 3.5.14 4.0.6"}
JAVAS=${JAVAS:-"17 21 25"}

failures=()

for sb in $SPRING_BOOTS; do
    for j in $JAVAS; do
        # Spring Boot 3.2.x is on extended support — skip Java 25, the toolchain
        # combination is not part of the supported matrix.
        if [[ "$sb" == 3.2.* && "$j" == "25" ]]; then
            echo "--- SKIP: Spring Boot $sb on Java $j (extended-support combo)"
            continue
        fi

        echo
        echo "=== Spring Boot $sb on Java $j ==="
        if ./gradlew clean test \
                -PspringBootVersion="$sb" \
                -PjavaToolchain="$j" \
                --console=plain; then
            echo "    OK"
        else
            echo "    FAILED"
            failures+=("$sb / $j")
        fi
    done
done

echo
if [[ ${#failures[@]} -eq 0 ]]; then
    echo "All combinations passed."
else
    echo "Failures:"
    printf '  - %s\n' "${failures[@]}"
    exit 1
fi
